// Zalith Launcher 2 Plus
//
// See vrs_shim.h for the overall approach and honest status.
//
// Config: read once from the ZALITH_VRS_RATE env var, set (from Kotlin, before Vulkan loads)
// as "WxH" e.g. "2x2", or unset/"0" to disable. Values must be powers of two per
// vkCmdSetFragmentShadingRateKHR's own VUIDs (VUID-vkCmdSetFragmentShadingRateKHR-
// pFragmentSize-04515/04516) — this file does not validate that beyond a basic sanity check,
// since the setting UI (VrsSetting.kt) is the one responsible for only ever offering valid
// choices in the first place.

#include "vrs_shim.h"

#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

#include "logger/logger.h"

#define VRS_LOG_TAG "VrsShim"
#define VRS_LOGE(fmt, ...) LOG_TO_E("<%s> " fmt, VRS_LOG_TAG, ##__VA_ARGS__)
#define VRS_LOGI(fmt, ...) LOG_TO_I("<%s> " fmt, VRS_LOG_TAG, ##__VA_ARGS__)

static void* g_real_handle = NULL;
static PFN_vkGetInstanceProcAddr g_real_gipa = NULL;

static int g_vrs_width = 0;
static int g_vrs_height = 0;
static VkFragmentShadingRateCombinerOpKHR g_combiner_ops[2] = {
    VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR,
    VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR
};

// Cached per-device once vkCreateDevice succeeds. This app only ever runs one game device at
// a time, so a single global is fine — this would need to become a table keyed by VkDevice if
// that assumption ever changes.
static PFN_vkCmdSetFragmentShadingRateKHR g_cmd_set_rate = NULL;
static PFN_vkGetDeviceProcAddr g_real_gdpa = NULL;

static PFN_vkVoidFunction VKAPI_PTR wrapped_gipa(VkInstance instance, const char* pName);
static PFN_vkVoidFunction VKAPI_PTR wrapped_gdpa(VkDevice device, const char* pName);

static VkResult VKAPI_PTR wrapped_create_device(
    VkPhysicalDevice physicalDevice,
    const VkDeviceCreateInfo* pCreateInfo,
    const VkAllocationCallbacks* pAllocator,
    VkDevice* pDevice
);

static VkResult VKAPI_PTR wrapped_create_graphics_pipelines(
    VkDevice device,
    VkPipelineCache pipelineCache,
    uint32_t createInfoCount,
    const VkGraphicsPipelineCreateInfo* pCreateInfos,
    const VkAllocationCallbacks* pAllocator,
    VkPipeline* pPipelines
);

static void VKAPI_PTR wrapped_cmd_begin_rendering(VkCommandBuffer commandBuffer, const VkRenderingInfo* pRenderingInfo);
static PFN_vkCmdBeginRenderingKHR g_real_begin_rendering = NULL;

// ---------------------------------------------------------------------------------------
// Setup / config
// ---------------------------------------------------------------------------------------

static int vrs_config_loaded = 0;

static void load_vrs_config(void) {
    if (vrs_config_loaded) return;
    vrs_config_loaded = 1;

    const char* rate = getenv("ZALITH_VRS_RATE");
    if (!rate || strcmp(rate, "0") == 0 || rate[0] == '\0') {
        g_vrs_width = 0;
        g_vrs_height = 0;
        return;
    }

    int w = 0, h = 0;
    if (sscanf(rate, "%dx%d", &w, &h) != 2 || w <= 0 || h <= 0) {
        VRS_LOGE("Malformed ZALITH_VRS_RATE '%s', disabling VRS", rate);
        g_vrs_width = 0;
        g_vrs_height = 0;
        return;
    }

    g_vrs_width = w;
    g_vrs_height = h;
    // combinerOps[0] REPLACE so our dynamic call actually overrides the pipeline's own
    // fragmentSize (VkPipelineFragmentShadingRateStateCreateInfoKHR, which we don't set —
    // pipelines default to 1x1). combinerOps[1] must stay KEEP since we never enable
    // attachmentFragmentShadingRate (VUID-vkCmdSetFragmentShadingRateKHR-
    // attachmentFragmentShadingRate-04511).
    g_combiner_ops[0] = VK_FRAGMENT_SHADING_RATE_COMBINER_OP_REPLACE_KHR;
    g_combiner_ops[1] = VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR;

    VRS_LOGI("VRS enabled at %dx%d", w, h);
}

void* vrs_maybe_wrap_vulkan(void* real_handle) {
    load_vrs_config();
    if (g_vrs_width == 0 || real_handle == NULL) {
        return real_handle;
    }

    g_real_handle = real_handle;
    g_real_gipa = (PFN_vkGetInstanceProcAddr) dlsym(real_handle, "vkGetInstanceProcAddr");
    if (!g_real_gipa) {
        VRS_LOGE("Real driver has no vkGetInstanceProcAddr, disabling VRS");
        return real_handle;
    }

    // Return a handle to OUR OWN already-loaded library so LWJGL's dlsym() on it finds our
    // exported vkGetInstanceProcAddr below instead of the real driver's. pojavexec.so is
    // built with -rdynamic specifically to make symbols like this dlsym-able; RTLD_NOLOAD
    // guarantees we get a handle to the copy already mapped into this process rather than
    // loading a second one.
    //
    // UNVERIFIED: this exact self-referential dlopen(RTLD_NOLOAD) pattern is standard on
    // glibc but Android's Bionic linker has its own namespace-isolation quirks (this repo
    // already works around some of them in driver_helper/nsbypass.c). Whether it behaves
    // identically here needs confirming on-device — if dlsym on this handle can't find
    // vkGetInstanceProcAddr below, LWJGL will crash on Vulkan init rather than silently
    // falling back, so this is worth testing with VRS explicitly OFF first to confirm
    // nothing regresses, then ON.
    void* self = dlopen("libpojavexec.so", RTLD_NOW | RTLD_NOLOAD);
    if (!self) {
        VRS_LOGE("Couldn't get a self-handle to libpojavexec.so (%s), disabling VRS", dlerror());
        return real_handle;
    }
    return self;
}

// ---------------------------------------------------------------------------------------
// vkGetInstanceProcAddr / vkGetDeviceProcAddr
// ---------------------------------------------------------------------------------------

// Exported (default visibility via -rdynamic) so dlsym() on our self-handle finds it.
PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char* pName) {
    if (strcmp(pName, "vkGetInstanceProcAddr") == 0) {
        return (PFN_vkVoidFunction) wrapped_gipa;
    }
    if (strcmp(pName, "vkCreateDevice") == 0) {
        return (PFN_vkVoidFunction) wrapped_create_device;
    }
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) {
        return (PFN_vkVoidFunction) wrapped_gdpa;
    }
    return g_real_gipa(instance, pName);
}

static PFN_vkVoidFunction VKAPI_PTR wrapped_gipa(VkInstance instance, const char* pName) {
    return vkGetInstanceProcAddr(instance, pName);
}

static PFN_vkVoidFunction VKAPI_PTR wrapped_gdpa(VkDevice device, const char* pName) {
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) {
        return (PFN_vkVoidFunction) wrapped_gdpa;
    }
    if (strcmp(pName, "vkCreateGraphicsPipelines") == 0) {
        return (PFN_vkVoidFunction) wrapped_create_graphics_pipelines;
    }
    // VK_KHR_dynamic_rendering is already in this app's own REQUIRED_EXTENSIONS
    // (VulkanCapabilities.kt), so vkCmdBeginRenderingKHR — not legacy vkCmdBeginRenderPass —
    // is almost certainly the only render-pass-entry call Zink actually issues here. Legacy
    // render passes are deliberately NOT wrapped: adding an untested code path for something
    // this app's own renderer requirements say shouldn't be reachable isn't worth the risk.
    if (strcmp(pName, "vkCmdBeginRenderingKHR") == 0 || strcmp(pName, "vkCmdBeginRendering") == 0) {
        g_real_begin_rendering = (PFN_vkCmdBeginRenderingKHR) g_real_gdpa(device, pName);
        return (PFN_vkVoidFunction) wrapped_cmd_begin_rendering;
    }
    return g_real_gdpa(device, pName);
}

// ---------------------------------------------------------------------------------------
// vkCreateDevice — enable the extension + pipelineFragmentShadingRate feature
// ---------------------------------------------------------------------------------------

static VkResult VKAPI_PTR wrapped_create_device(
    VkPhysicalDevice physicalDevice,
    const VkDeviceCreateInfo* pCreateInfo,
    const VkAllocationCallbacks* pAllocator,
    VkDevice* pDevice
) {
    PFN_vkCreateDevice real_create_device =
        (PFN_vkCreateDevice) g_real_gipa(NULL, "vkCreateDevice");
    if (!real_create_device) {
        VRS_LOGE("Real driver has no vkCreateDevice — this shouldn't happen");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    VkPhysicalDeviceFragmentShadingRateFeaturesKHR vrs_features = {0};
    vrs_features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_FEATURES_KHR;
    vrs_features.pNext = (void*) pCreateInfo->pNext;
    vrs_features.pipelineFragmentShadingRate = VK_TRUE;
    vrs_features.primitiveFragmentShadingRate = VK_FALSE;
    vrs_features.attachmentFragmentShadingRate = VK_FALSE;

    // Copy the extension name list, appending ours if Zink didn't already request it
    // (it won't have — this app's REQUIRED_EXTENSIONS list doesn't include it).
    uint32_t new_ext_count = pCreateInfo->enabledExtensionCount + 1;
    const char** new_exts = (const char**) malloc(sizeof(const char*) * new_ext_count);
    if (!new_exts) {
        VRS_LOGE("OOM patching device extension list, falling back to unmodified vkCreateDevice");
        return real_create_device(physicalDevice, pCreateInfo, pAllocator, pDevice);
    }
    int already_present = 0;
    for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount; i++) {
        new_exts[i] = pCreateInfo->ppEnabledExtensionNames[i];
        if (strcmp(new_exts[i], VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME) == 0) {
            already_present = 1;
        }
    }
    new_exts[pCreateInfo->enabledExtensionCount] = VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME;

    VkDeviceCreateInfo patched = *pCreateInfo;
    patched.pNext = &vrs_features;
    if (!already_present) {
        patched.enabledExtensionCount = new_ext_count;
        patched.ppEnabledExtensionNames = new_exts;
    }

    VkResult result = real_create_device(physicalDevice, &patched, pAllocator, pDevice);
    free(new_exts);

    if (result != VK_SUCCESS) {
        VRS_LOGE("Patched vkCreateDevice failed (%d) — driver may not actually "
                              "support pipelineFragmentShadingRate despite advertising the "
                              "extension. Falling back to the original, unmodified call.", result);
        return real_create_device(physicalDevice, pCreateInfo, pAllocator, pDevice);
    }

    g_real_gdpa = (PFN_vkGetDeviceProcAddr) g_real_gipa(NULL, "vkGetDeviceProcAddr");
    if (g_real_gdpa) {
        g_cmd_set_rate = (PFN_vkCmdSetFragmentShadingRateKHR)
            g_real_gdpa(*pDevice, "vkCmdSetFragmentShadingRateKHR");
    }
    if (!g_cmd_set_rate) {
        VRS_LOGE("Device created with the feature enabled but couldn't resolve "
                              "vkCmdSetFragmentShadingRateKHR — VRS will be inert this session");
    }

    return result;
}

// ---------------------------------------------------------------------------------------
// vkCreateGraphicsPipelines — force VK_DYNAMIC_STATE_FRAGMENT_SHADING_RATE_KHR onto every
// pipeline, since vkCmdSetFragmentShadingRateKHR only has an effect on pipelines created
// with that dynamic state (or an explicit VkPipelineFragmentShadingRateStateCreateInfoKHR,
// which we're not setting — dynamic state is simpler and doesn't need per-pipeline pNext
// chain surgery beyond the dynamic state list itself).
// ---------------------------------------------------------------------------------------

static VkResult VKAPI_PTR wrapped_create_graphics_pipelines(
    VkDevice device,
    VkPipelineCache pipelineCache,
    uint32_t createInfoCount,
    const VkGraphicsPipelineCreateInfo* pCreateInfos,
    const VkAllocationCallbacks* pAllocator,
    VkPipeline* pPipelines
) {
    PFN_vkCreateGraphicsPipelines real_fn =
        (PFN_vkCreateGraphicsPipelines) g_real_gdpa(device, "vkCreateGraphicsPipelines");
    if (!real_fn) {
        VRS_LOGE("Real driver has no vkCreateGraphicsPipelines — this shouldn't happen");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    VkGraphicsPipelineCreateInfo* patched_infos =
        (VkGraphicsPipelineCreateInfo*) malloc(sizeof(VkGraphicsPipelineCreateInfo) * createInfoCount);
    VkPipelineDynamicStateCreateInfo* patched_dynamic =
        (VkPipelineDynamicStateCreateInfo*) malloc(sizeof(VkPipelineDynamicStateCreateInfo) * createInfoCount);
    // Worst case every pipeline's existing dynamic state list needs +1 entry — allocate one
    // scratch array per pipeline rather than trying to share a single buffer, since each
    // pipeline's existing dynamic state count can differ.
    VkDynamicState** patched_states = (VkDynamicState**) calloc(createInfoCount, sizeof(VkDynamicState*));

    if (!patched_infos || !patched_dynamic || !patched_states) {
        VRS_LOGE("OOM patching pipeline dynamic state, falling back to unmodified call");
        free(patched_infos);
        free(patched_dynamic);
        free(patched_states);
        return real_fn(device, pipelineCache, createInfoCount, pCreateInfos, pAllocator, pPipelines);
    }

    for (uint32_t i = 0; i < createInfoCount; i++) {
        patched_infos[i] = pCreateInfos[i];
        const VkPipelineDynamicStateCreateInfo* orig_dyn = pCreateInfos[i].pDynamicState;
        uint32_t orig_count = orig_dyn ? orig_dyn->dynamicStateCount : 0;

        VkDynamicState* states = (VkDynamicState*) malloc(sizeof(VkDynamicState) * (orig_count + 1));
        if (!states) {
            // Leave this one pipeline un-patched rather than aborting the whole batch —
            // it just won't get VRS applied, everything else in the batch still can.
            continue;
        }
        for (uint32_t j = 0; j < orig_count; j++) {
            states[j] = orig_dyn->pDynamicStates[j];
        }
        states[orig_count] = VK_DYNAMIC_STATE_FRAGMENT_SHADING_RATE_KHR;
        patched_states[i] = states;

        patched_dynamic[i] = (VkPipelineDynamicStateCreateInfo){0};
        patched_dynamic[i].sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        patched_dynamic[i].pNext = orig_dyn ? orig_dyn->pNext : NULL;
        patched_dynamic[i].flags = orig_dyn ? orig_dyn->flags : 0;
        patched_dynamic[i].dynamicStateCount = orig_count + 1;
        patched_dynamic[i].pDynamicStates = states;

        patched_infos[i].pDynamicState = &patched_dynamic[i];
    }

    VkResult result = real_fn(device, pipelineCache, createInfoCount, patched_infos, pAllocator, pPipelines);

    for (uint32_t i = 0; i < createInfoCount; i++) {
        free(patched_states[i]);
    }
    free(patched_states);
    free(patched_dynamic);
    free(patched_infos);

    if (result != VK_SUCCESS) {
        VRS_LOGE("Patched vkCreateGraphicsPipelines failed (%d) — falling back to "
                              "the original, unmodified call. Pipelines from this batch will "
                              "not have VRS applied.", result);
        return real_fn(device, pipelineCache, createInfoCount, pCreateInfos, pAllocator, pPipelines);
    }

    return result;
}

// ---------------------------------------------------------------------------------------
// vkCmdBeginRenderingKHR — set the dynamic shading rate right after the real call.
// ---------------------------------------------------------------------------------------

static void VKAPI_PTR wrapped_cmd_begin_rendering(VkCommandBuffer commandBuffer, const VkRenderingInfo* pRenderingInfo) {
    g_real_begin_rendering(commandBuffer, pRenderingInfo);

    if (!g_cmd_set_rate) return;

    VkExtent2D fragment_size = { (uint32_t) g_vrs_width, (uint32_t) g_vrs_height };
    g_cmd_set_rate(commandBuffer, &fragment_size, g_combiner_ops);
}
