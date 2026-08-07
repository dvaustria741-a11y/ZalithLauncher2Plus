#include "fsr_hook.h"
#include "fsr_shader.h"

#include <cmath>
#include <cstring>
#include <time.h>

static bool g_initialized = false;
static bool g_active = false;
static bool g_hooksActive = false;
static int g_qualityPreset = 2;

/*
 * Adaptive mode: instead of a fixed preset, the render scale is continuously
 * adjusted based on measured real frame time vs. a target frame time. This
 * reuses the exact same downscale-render + FSR-upscale pipeline as the manual
 * preset mode - only what drives g_renderWidth/g_renderHeight differs.
 */
static bool g_adaptiveEnabled = false;
static float g_targetFrameTimeMs = 16.6f; // derived from target FPS
static float g_currentScale = 1.5f;       // continuous equivalent of g_qualityPreset
static constexpr float kMinScale = 1.0f;  // native resolution, no downscale
static constexpr float kMaxScale = 2.2f;  // don't go below ~45% render resolution
static constexpr float kScaleStep = 0.1f;
static constexpr int kWindowFrames = 30;         // ~0.5s at 60fps, evaluate every N frames
static constexpr float kOverBudgetRatio = 1.15f;  // step down if avg frame time exceeds target by this much
static constexpr float kUnderBudgetRatio = 0.85f; // step up (raise res) if comfortably under target
static constexpr int64_t kCooldownNs = 1000000000LL; // don't adjust more than once per second

static int64_t g_lastFrameTimeNs = 0;
static int64_t g_lastAdjustTimeNs = 0;
static double g_frameTimeAccumMs = 0.0;
static int g_frameTimeSampleCount = 0;

static int64_t nowNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

static GLuint g_renderFBO = 0;
static GLuint g_renderTexture = 0;
static GLuint g_depthStencilRBO = 0;
static GLuint g_targetFBO = 0;
static GLuint g_targetTexture = 0;
static GLuint g_quadVAO = 0;
static GLuint g_quadVBO = 0;
static GLuint g_fsrProgram = 0;

static GLsizei g_renderWidth = 0;
static GLsizei g_renderHeight = 0;
static GLsizei g_targetWidth = 0;
static GLsizei g_targetHeight = 0;

/* Real function pointers for intercepted GL functions */
static void (*real_glBindFramebuffer)(GLenum target, GLuint framebuffer) = nullptr;
static void (*real_glViewport)(GLint x, GLint y, GLsizei width, GLsizei height) = nullptr;
static void (*real_glGetIntegerv)(GLenum pname, GLint* data) = nullptr;
static void* (*real_eglGetProcAddress)(const char* procname) = nullptr;
static void (*real_glGenVertexArrays)(GLsizei n, GLuint* arrays) = nullptr;
static void (*real_glBindVertexArray)(GLuint array) = nullptr;
static void (*real_glDeleteVertexArrays)(GLsizei n, const GLuint* arrays) = nullptr;
static void (*real_glBlitFramebuffer)(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter) = nullptr;

static void checkError(const char* tag) {
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGE("%s: GL error 0x%x", tag, err);
    }
}

static void calcRenderResolution(int targetW, int targetH, int preset, int* outW, int* outH) {
    float scale;
    switch (preset) {
        case 1: scale = 1.3f; break;
        case 2: scale = 1.5f; break;
        case 3: scale = 1.7f; break;
        case 4: scale = 2.0f; break;
        default: scale = 1.5f; break;
    }
    *outW = (int)(targetW / scale);
    *outH = (int)(targetH / scale);
    *outW = (*outW + 1) & ~1;
    *outH = (*outH + 1) & ~1;
}

static void calcRenderResolutionFromScale(int targetW, int targetH, float scale, int* outW, int* outH) {
    *outW = (int)(targetW / scale);
    *outH = (int)(targetH / scale);
    *outW = (*outW + 1) & ~1;
    *outH = (*outH + 1) & ~1;
}

/* Single dispatch point so surface-resize handling and init both stay in sync
 * with whichever mode (manual preset vs. adaptive) is currently driving the scale. */
static void recomputeRenderResolution(int* outW, int* outH) {
    if (g_adaptiveEnabled) {
        calcRenderResolutionFromScale(g_targetWidth, g_targetHeight, g_currentScale, outW, outH);
    } else {
        calcRenderResolution(g_targetWidth, g_targetHeight, g_qualityPreset, outW, outH);
    }
}

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[512];
        glGetShaderInfoLog(shader, 512, nullptr, log);
        LOGE("Shader compile error (%s): %s", type == GL_VERTEX_SHADER ? "VS" : "FS", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static void* resolveFromGLES(const char* name) {
    // Load directly from GLES libs — never through eglGetProcAddress which may be
    // hooked by the renderer (e.g. Krypton Wrapper), causing circular resolution.
    static void* gles = nullptr;
    if (!gles) {
        gles = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_NOLOAD);
        if (!gles) gles = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_NOLOAD);
        if (!gles) gles = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_LOCAL);
        if (!gles) gles = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
    }
    void* sym = gles ? dlsym(gles, name) : nullptr;
    if (!sym) sym = dlsym(RTLD_DEFAULT, name);
    return sym;
}

/*
 * Returns true if all real GL function pointers were successfully resolved.
 * Resets all pointers to nullptr and returns false if any are missing,
 * so the next call can retry resolution (e.g. after the GL context is ready).
 */
static bool getRealGLFunctions() {
    if (real_glBindFramebuffer) return true; // already resolved successfully
    real_glBindFramebuffer  = (void (*)(GLenum, GLuint))         resolveFromGLES("glBindFramebuffer");
    real_glViewport         = (void (*)(GLint, GLint, GLsizei, GLsizei)) resolveFromGLES("glViewport");
    real_glGetIntegerv      = (void (*)(GLenum, GLint*))          resolveFromGLES("glGetIntegerv");
    real_glGenVertexArrays  = (void (*)(GLsizei, GLuint*))        resolveFromGLES("glGenVertexArrays");
    real_glBindVertexArray  = (void (*)(GLuint))                  resolveFromGLES("glBindVertexArray");
    real_glDeleteVertexArrays = (void (*)(GLsizei, const GLuint*))resolveFromGLES("glDeleteVertexArrays");
    real_glBlitFramebuffer  = (void (*)(GLint,GLint,GLint,GLint,GLint,GLint,GLint,GLint,GLbitfield,GLenum)) resolveFromGLES("glBlitFramebuffer");
    if (!real_glBindFramebuffer || !real_glViewport || !real_glGetIntegerv ||
        !real_glGenVertexArrays || !real_glBindVertexArray ||
        !real_glDeleteVertexArrays || !real_glBlitFramebuffer) {
        LOGE("FSR: failed to resolve real GL functions — aborting hook installation");
        // Reset all to nullptr so a future call can retry once the GL context is ready
        real_glBindFramebuffer    = nullptr;
        real_glViewport           = nullptr;
        real_glGetIntegerv        = nullptr;
        real_glGenVertexArrays    = nullptr;
        real_glBindVertexArray    = nullptr;
        real_glDeleteVertexArrays = nullptr;
        real_glBlitFramebuffer    = nullptr;
        return false;
    }
    return true;
}

/*
 * Hooked eglGetProcAddress — returns our wrapper for intercepted functions,
 * passes through everything else to the real eglGetProcAddress.
 * Called from any library whose PLT entry for eglGetProcAddress was hooked by bytehook.
 */
extern "C" void* hook_eglGetProcAddress(const char* name) {
    if (strcmp(name, "glBindFramebuffer") == 0) return (void*)glBindFramebuffer;
    if (strcmp(name, "glViewport") == 0) return (void*)glViewport;
    if (strcmp(name, "glGetIntegerv") == 0) return (void*)glGetIntegerv;
    return real_eglGetProcAddress(name);
}

/*
 * Exported wrapper — when the game binds framebuffer 0 (the default / EGL surface),
 * redirect to our lower-resolution render FBO so the game renders at reduced resolution.
 *
 * Safety: guard against null real_glBindFramebuffer in case hooks fired before
 * GL resolution completed (e.g. during EGL context setup with MobileGlues/ANGLE).
 */
extern "C" void glBindFramebuffer(GLenum target, GLuint framebuffer) {
    if (!real_glBindFramebuffer) {
        getRealGLFunctions();
        if (!real_glBindFramebuffer) return;
    }
    if (g_active && g_renderFBO != 0 && framebuffer == 0) {
        real_glBindFramebuffer(target, g_renderFBO);
        return;
    }
    real_glBindFramebuffer(target, framebuffer);
}

/*
 * Exported wrapper — clamp viewport to the render resolution when FSR is active.
 * This ensures the rasterizer only generates fragments within the lower-res FBO,
 * delivering the full FPS gain from reduced pixel processing.
 *
 * Safety: guard against null real_glViewport (same timing issue as glBindFramebuffer).
 */
extern "C" void glViewport(GLint x, GLint y, GLsizei width, GLsizei height) {
    if (!real_glViewport) {
        getRealGLFunctions();
        if (!real_glViewport) return;
    }
    if (g_active) {
        GLsizei maxW = (GLsizei)g_renderWidth - x;
        GLsizei maxH = (GLsizei)g_renderHeight - y;
        if (maxW < 0) maxW = 0;
        if (maxH < 0) maxH = 0;
        real_glViewport(x, y,
            width < maxW ? width : maxW,
            height < maxH ? height : maxH);
        return;
    }
    real_glViewport(x, y, width, height);
}

/*
 * Exported wrapper — spoof GL_FRAMEBUFFER_BINDING queries so the game always sees 0
 * when our redirect FBO is active. This prevents state save/restore breakage.
 *
 * Safety: guard against null real_glGetIntegerv. This is the function that was crashing
 * (SIGSEGV at pc=0x0 in glGetIntegerv+0x1c) when hooks fired before MobileGlues/ANGLE
 * had finished binding its GL entry points.
 */
extern "C" void glGetIntegerv(GLenum pname, GLint* data) {
    if (!real_glGetIntegerv) {
        getRealGLFunctions();
        if (!real_glGetIntegerv) return;
    }
    real_glGetIntegerv(pname, data);
    if (g_active && g_renderFBO != 0) {
        if (pname == GL_FRAMEBUFFER_BINDING &&
            (GLuint)data[0] == g_renderFBO) {
            data[0] = 0;
        }
    }
}

static bool initHooks() {
    void* bh = dlopen("libbytehook.so", RTLD_NOW);
    if (!bh) {
        LOGD("bytehook not available — FSR running without FPS gain");
        return false;
    }

    int (*bytehook_init)(int mode, bool debug) =
        (int (*)(int, bool))dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        (void* (*)(const char*, const char*, void*, void*, void*))dlsym(bh, "bytehook_hook_all");

    if (!bytehook_init || !bytehook_hook_all) {
        LOGD("bytehook symbols not found");
        dlclose(bh);
        return false;
    }

    if (bytehook_init(0, false) != 0) {
        LOGD("bytehook init failed");
        dlclose(bh);
        return false;
    }

    bytehook_hook_all(nullptr, "eglGetProcAddress", (void*)hook_eglGetProcAddress, nullptr, nullptr);
    bytehook_hook_all(nullptr, "glBindFramebuffer", (void*)glBindFramebuffer, nullptr, nullptr);
    bytehook_hook_all(nullptr, "glViewport", (void*)glViewport, nullptr, nullptr);
    bytehook_hook_all(nullptr, "glGetIntegerv", (void*)glGetIntegerv, nullptr, nullptr);

    LOGD("FSR: bytehook installed — all hooks active");
    return true;
}

static bool initFSRResources() {
    GLint prevProgram, prevVAO, prevArrayBuffer, prevTexture, prevFBO;
    glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &prevVAO);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &prevArrayBuffer);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTexture);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFBO);

    GLuint vs = compileShader(GL_VERTEX_SHADER, FSR_VSSource);
    if (!vs) { LOGE("Failed to compile FSR vertex shader"); return false; }

    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FSR_FSSource);
    if (!fs) { LOGE("Failed to compile FSR fragment shader"); glDeleteShader(vs); return false; }

    g_fsrProgram = glCreateProgram();
    glAttachShader(g_fsrProgram, vs);
    glAttachShader(g_fsrProgram, fs);
    glLinkProgram(g_fsrProgram);

    GLint status;
    glGetProgramiv(g_fsrProgram, GL_LINK_STATUS, &status);
    if (!status) {
        char log[512];
        glGetProgramInfoLog(g_fsrProgram, 512, nullptr, log);
        LOGE("FSR program link error: %s", log);
        glDeleteShader(vs); glDeleteShader(fs);
        glDeleteProgram(g_fsrProgram); g_fsrProgram = 0;
        return false;
    }
    glDeleteShader(vs); glDeleteShader(fs);

    const float quadVertices[] = {
        -1.0f,  1.0f, 0.0f, 1.0f,
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
         1.0f,  1.0f, 1.0f, 1.0f
    };

    real_glGenVertexArrays(1, &g_quadVAO);
    glGenBuffers(1, &g_quadVBO);
    real_glBindVertexArray(g_quadVAO);
    glBindBuffer(GL_ARRAY_BUFFER, g_quadVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quadVertices), quadVertices, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));
    glEnableVertexAttribArray(1);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    real_glBindVertexArray(0);

    glGenTextures(1, &g_renderTexture);
    glBindTexture(GL_TEXTURE_2D, g_renderTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g_renderWidth, g_renderHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenRenderbuffers(1, &g_depthStencilRBO);
    glBindRenderbuffer(GL_RENDERBUFFER, g_depthStencilRBO);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, g_renderWidth, g_renderHeight);

    glGenFramebuffers(1, &g_renderFBO);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_renderFBO);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_renderTexture, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g_depthStencilRBO);

    glGenTextures(1, &g_targetTexture);
    glBindTexture(GL_TEXTURE_2D, g_targetTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g_targetWidth, g_targetHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenFramebuffers(1, &g_targetFBO);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_targetFBO);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_targetTexture, 0);

    checkError("initFSRResources");

    glUseProgram(prevProgram);
    real_glBindVertexArray(prevVAO);
    glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuffer);
    glBindTexture(GL_TEXTURE_2D, prevTexture);
    real_glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);

    LOGD("FSR initialized: render %dx%d target %dx%d", g_renderWidth, g_renderHeight, g_targetWidth, g_targetHeight);
    return true;
}

extern "C" void fsr_init(int qualityPreset) {
    g_qualityPreset = qualityPreset;
    g_initialized = false;
    g_active = false;

    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) {
        LOGD("FSR init deferred (no current context)");
        g_initialized = true;
        return;
    }

    // Resolve real GL function pointers before installing any hooks.
    // If resolution fails (e.g. MobileGlues/ANGLE hasn't finished binding yet),
    // abort entirely — installing hooks with null pointers causes SIGSEGV
    // when EGLBridge internally calls glGetIntegerv during context setup.
    if (!getRealGLFunctions()) {
        LOGE("FSR: GL function resolution failed — FSR disabled to prevent crash");
        g_initialized = true;
        return;
    }

    if (!g_hooksActive) {
        g_hooksActive = initHooks();
    }

    EGLint targetW = 0, targetH = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &targetW);
    eglQuerySurface(display, surface, EGL_HEIGHT, &targetH);
    if (targetW <= 0 || targetH <= 0) {
        LOGE("FSR: invalid surface dimensions %dx%d", targetW, targetH);
        return;
    }

    g_targetWidth = targetW;
    g_targetHeight = targetH;
    recomputeRenderResolution(&g_renderWidth, &g_renderHeight);

    if (g_renderWidth <= 0 || g_renderHeight <= 0) {
        LOGE("FSR: invalid render dimensions %dx%d", g_renderWidth, g_renderHeight);
        return;
    }

    if (g_renderWidth >= g_targetWidth && g_renderHeight >= g_targetHeight) {
        LOGD("FSR: render res >= target, skipping");
        return;
    }

    if (!initFSRResources()) {
        LOGE("FSR resource init failed");
        return;
    }

    g_active = true;
    if (!g_hooksActive) {
        LOGD("FSR active (no FPS gain — no bytehook)");
    } else {
        LOGD("FSR active with FPS gain — fb/wvp hooks installed");
    }

    real_glViewport(0, 0, g_renderWidth, g_renderHeight);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_renderFBO);
}

static bool fsrRebuildFramebuffers() {
    GLint prevTexture, prevFBO, prevRBO;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTexture);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFBO);
    glGetIntegerv(GL_RENDERBUFFER_BINDING, &prevRBO);

    if (g_renderTexture) glDeleteTextures(1, &g_renderTexture);
    if (g_depthStencilRBO) glDeleteRenderbuffers(1, &g_depthStencilRBO);
    if (g_renderFBO) glDeleteFramebuffers(1, &g_renderFBO);
    if (g_targetTexture) glDeleteTextures(1, &g_targetTexture);
    if (g_targetFBO) glDeleteFramebuffers(1, &g_targetFBO);
    g_renderTexture = g_depthStencilRBO = g_renderFBO = g_targetTexture = g_targetFBO = 0;

    glGenTextures(1, &g_renderTexture);
    glBindTexture(GL_TEXTURE_2D, g_renderTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g_renderWidth, g_renderHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenRenderbuffers(1, &g_depthStencilRBO);
    glBindRenderbuffer(GL_RENDERBUFFER, g_depthStencilRBO);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, g_renderWidth, g_renderHeight);

    glGenFramebuffers(1, &g_renderFBO);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_renderFBO);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_renderTexture, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g_depthStencilRBO);

    glGenTextures(1, &g_targetTexture);
    glBindTexture(GL_TEXTURE_2D, g_targetTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g_targetWidth, g_targetHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenFramebuffers(1, &g_targetFBO);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_targetFBO);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_targetTexture, 0);

    glBindTexture(GL_TEXTURE_2D, prevTexture);
    glBindRenderbuffer(GL_RENDERBUFFER, prevRBO);
    real_glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);

    checkError("fsrRebuildFramebuffers");
    return true;
}

/* Shared by both surface-resize handling and adaptive scale changes: given a
 * freshly recomputed g_renderWidth/g_renderHeight, rebuild the FBOs at the new
 * size and rebind. Returns false (and disables FSR) if the rebuild fails. */
static bool applyNewRenderResolution() {
    if (g_renderWidth >= g_targetWidth || g_renderHeight >= g_targetHeight) {
        return true; // nothing to do, downscale would be a no-op
    }
    if (!fsrRebuildFramebuffers()) {
        LOGE("FSR: framebuffer rebuild failed, disabling");
        g_active = false;
        return false;
    }
    real_glViewport(0, 0, g_renderWidth, g_renderHeight);
    real_glBindFramebuffer(GL_FRAMEBUFFER, g_renderFBO);
    return true;
}

/*
 * Called once per frame from fsr_apply(), before the resize/render logic.
 * Tracks a rolling window of real frame-to-frame time and, in adaptive mode,
 * nudges g_currentScale up or down to chase g_targetFrameTimeMs. Deliberately
 * conservative: evaluates only every kWindowFrames frames and enforces a
 * cooldown between changes, so a single stutter (loading a chunk, GC pause)
 * doesn't cause visible resolution flicker - only a sustained trend does.
 */
static void adaptiveTick() {
    int64_t now = nowNs();
    if (g_lastFrameTimeNs == 0) {
        // first frame since FSR became active - nothing to compare against yet
        g_lastFrameTimeNs = now;
        g_lastAdjustTimeNs = now;
        return;
    }

    double deltaMs = (double)(now - g_lastFrameTimeNs) / 1e6;
    g_lastFrameTimeNs = now;

    if (!g_adaptiveEnabled) return;

    // Ignore outlier frames (app backgrounded, breakpoint, huge stutter) so
    // they don't dominate the rolling average and cause an overreaction.
    if (deltaMs > 250.0) return;

    g_frameTimeAccumMs += deltaMs;
    g_frameTimeSampleCount++;
    if (g_frameTimeSampleCount < kWindowFrames) return;

    double avgMs = g_frameTimeAccumMs / g_frameTimeSampleCount;
    g_frameTimeAccumMs = 0.0;
    g_frameTimeSampleCount = 0;

    if (now - g_lastAdjustTimeNs < kCooldownNs) return;

    float newScale = g_currentScale;
    if (avgMs > g_targetFrameTimeMs * kOverBudgetRatio && g_currentScale < kMaxScale) {
        newScale = g_currentScale + kScaleStep;
        if (newScale > kMaxScale) newScale = kMaxScale;
    } else if (avgMs < g_targetFrameTimeMs * kUnderBudgetRatio && g_currentScale > kMinScale) {
        newScale = g_currentScale - kScaleStep;
        if (newScale < kMinScale) newScale = kMinScale;
    } else {
        return; // within budget band, leave it alone
    }

    if (newScale == g_currentScale) return;

    LOGD("FSR adaptive: avg frame %.2fms vs target %.2fms, scale %.2f -> %.2f",
         avgMs, g_targetFrameTimeMs, g_currentScale, newScale);
    g_currentScale = newScale;
    g_lastAdjustTimeNs = now;

    recomputeRenderResolution(&g_renderWidth, &g_renderHeight);
    applyNewRenderResolution();
}

extern "C" void fsr_apply() {
    if (g_active) {
        adaptiveTick();

        EGLDisplay display = eglGetCurrentDisplay();
        EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
        if (display != EGL_NO_DISPLAY && surface != EGL_NO_SURFACE) {
            EGLint w, h;
            if (eglQuerySurface(display, surface, EGL_WIDTH, &w) &&
                eglQuerySurface(display, surface, EGL_HEIGHT, &h) &&
                (w != g_targetWidth || h != g_targetHeight)) {
                LOGD("FSR: surface resized %dx%d -> %dx%d", g_targetWidth, g_targetHeight, w, h);
                g_targetWidth = w;
                g_targetHeight = h;
                recomputeRenderResolution(&g_renderWidth, &g_renderHeight);
                applyNewRenderResolution();
            }
        }
        goto do_fsr;
    }
    if (g_initialized) {
        return;
    }
    {
        EGLDisplay display = eglGetCurrentDisplay();
        EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
        if (display != EGL_NO_DISPLAY && surface != EGL_NO_SURFACE) {
            fsr_init(g_qualityPreset);
            if (g_active) goto do_fsr;
        }
        g_initialized = true;
        return;
    }

do_fsr:
    {
        GLint prevProgram, prevVAO, prevArrayBuffer, prevActiveTexture, prevTexture;
        GLint prevReadFBO, prevDrawFBO, prevRenderbuffer;
        glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &prevVAO);
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &prevArrayBuffer);
        glGetIntegerv(GL_ACTIVE_TEXTURE, &prevActiveTexture);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTexture);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &prevReadFBO);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &prevDrawFBO);
        glGetIntegerv(GL_RENDERBUFFER_BINDING, &prevRenderbuffer);

        real_glBindFramebuffer(GL_FRAMEBUFFER, g_targetFBO);
        real_glViewport(0, 0, g_targetWidth, g_targetHeight);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(g_fsrProgram);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, g_renderTexture);

        float const0[4] = {
            (float)g_renderWidth / (float)g_targetWidth,
            (float)g_renderHeight / (float)g_targetHeight,
            1.0f / (float)g_targetWidth,
            1.0f / (float)g_targetHeight
        };
        float viewportSize[2] = { (float)g_renderWidth, (float)g_renderHeight };

        glUniform1i(glGetUniformLocation(g_fsrProgram, "uInputTex"), 0);
        glUniform4fv(glGetUniformLocation(g_fsrProgram, "uConst0"), 1, const0);
        glUniform2fv(glGetUniformLocation(g_fsrProgram, "uViewportSize"), 1, viewportSize);

        real_glBindVertexArray(g_quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        real_glBindVertexArray(0);

        real_glBindFramebuffer(GL_READ_FRAMEBUFFER, g_targetFBO);
        real_glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        real_glBlitFramebuffer(0, 0, g_targetWidth, g_targetHeight, 0, 0, g_targetWidth, g_targetHeight,
                               GL_COLOR_BUFFER_BIT, GL_LINEAR);

        real_glBindFramebuffer(GL_FRAMEBUFFER, g_renderFBO);
        real_glViewport(0, 0, g_renderWidth, g_renderHeight);

        glUseProgram(prevProgram);
        real_glBindVertexArray(prevVAO);
        glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuffer);
        glActiveTexture(prevActiveTexture);
        glBindTexture(GL_TEXTURE_2D, prevTexture);
        glBindRenderbuffer(GL_RENDERBUFFER, prevRenderbuffer);
        real_glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFBO);
        real_glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prevDrawFBO);

        checkError("fsr_apply");
    }
}

extern "C" void fsr_set_quality(int qualityPreset) {
    g_qualityPreset = qualityPreset;
}

extern "C" void fsr_destroy() {
    g_active = false;
    g_initialized = false;
    g_hooksActive = false;
    if (g_fsrProgram) { glDeleteProgram(g_fsrProgram); g_fsrProgram = 0; }
    if (g_quadVAO) { real_glDeleteVertexArrays(1, &g_quadVAO); g_quadVAO = 0; }
    if (g_quadVBO) { glDeleteBuffers(1, &g_quadVBO); g_quadVBO = 0; }
    if (g_renderFBO) { glDeleteFramebuffers(1, &g_renderFBO); g_renderFBO = 0; }
    if (g_renderTexture) { glDeleteTextures(1, &g_renderTexture); g_renderTexture = 0; }
    if (g_depthStencilRBO) { glDeleteRenderbuffers(1, &g_depthStencilRBO); g_depthStencilRBO = 0; }
    if (g_targetFBO) { glDeleteFramebuffers(1, &g_targetFBO); g_targetFBO = 0; }
    if (g_targetTexture) { glDeleteTextures(1, &g_targetTexture); g_targetTexture = 0; }
    LOGD("FSR destroyed");
}
