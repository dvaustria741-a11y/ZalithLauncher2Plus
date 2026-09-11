// Zalith Launcher 2 Plus
//
// Variable Rate Shading via a Vulkan function-pointer interposer.
//
// WHY THIS EXISTS: this launcher doesn't issue Vulkan draw calls itself — Minecraft's LWJGL
// does, and for the Vulkan-Zink renderer path those calls go through Mesa's Zink translation
// layer, which we don't control or build from source (it's a prebuilt driver .so). There's no
// way to "just call vkCmdSetFragmentShadingRateKHR" from our own code, because we never issue
// the draw calls or create the pipelines. Instead this wraps the Vulkan entry points LWJGL
// resolves, forwarding almost everything to the real driver, and rewriting the handful of
// calls that need to see a device with the feature enabled and pipelines created with the
// right dynamic state. See maybe_load_vulkan() in egl_bridge.c for where this plugs in —
// that's the existing hook already used to redirect libvulkan.so loads to Turnip.
//
// STATUS: implemented against the real, verified VK_KHR_fragment_shading_rate API (not
// guessed — see the struct/function signatures cited in comments below), but NOT yet run on
// a device. Confirmed on Dex's actual hardware (Adreno 619, driver 512.530.0) via
// vulkan.gpuinfo.org and Vulkan Hardware Capability Viewer: VK_KHR_fragment_shading_rate,
// VK_KHR_create_renderpass2, and VK_KHR_dynamic_rendering (already required by this app's
// own VulkanCapabilities.REQUIRED_EXTENSIONS) are all present. The extension being listed
// doesn't guarantee the *driver* handles this specific usage pattern correctly — Adreno 6xx
// Vulkan extension implementations have a real history of being technically-present-but-buggy
// for less-common extensions. Needs on-device testing (visual correctness + actual FPS
// measurement) before this is trusted.
//
// This is a BLUNT, whole-frame shading rate reduction, not the foveated (full detail near
// the player, reduced far away) approach originally described. True foveated rendering needs
// an attachment-based shading rate image tied to scene depth/distance, which needs far more
// integration with the renderer's actual scene structure than an external interposer can see.
// This applies one fixed rate to every pipeline, uniformly, whenever it's active.

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Called from maybe_load_vulkan() in egl_bridge.c, in place of returning real_handle
 * directly to LWJGL.
 *
 * @param real_handle the dlopen() handle for the actual Turnip/Zink driver, as already
 *   obtained by load_vulkan().
 * @return real_handle unchanged if VRS is disabled (via the ZALITH_VRS_RATE env var — see
 *   vrs_shim.c), so this is a true no-op with zero behavior change when the feature is off.
 *   If enabled, returns a handle whose vkGetInstanceProcAddr resolves to this file's wrapped
 *   entry points instead.
 */
void* vrs_maybe_wrap_vulkan(void* real_handle);

#ifdef __cplusplus
}
#endif
