# Frame Generation — integration status

Scaffolding only. Nothing here has been built or run on a device — treat every claim below
as "verified against source" or "still a guess," as marked.

## Verified against source (not guesses)
- The real CMake target is `lsfg-vk-framegen` (framegen/CMakeLists.txt), not `framegen` —
  first build attempt failed on this (`ld.lld: error: unable to find library -lframegen`),
  now fixed.
- The public API (`framegen/public/lsfg_3_1.hpp`) is a global `initialize(deviceUUID, isHdr,
  flowScale, generationCount, loader)` call, then per-swapchain `createContextFromAHB(...) ->
  int32_t`, then `presentContext(id, inSem, outSem)` per frame using fd-based semaphores —
  not the synchronous "hand me a buffer" shape the first version of this scaffold guessed.
  `frame_gen_jni.cpp` has been rewritten to match this.
- `initialize`'s `loader` callback needs shader bytecode by name. That comes from
  `Extract::extractShaders()` / `Extract::getShader()` (native/lsfg-vk-android's
  `src/extract/*.cpp`), which belongs to the `lsfg-vk` target, not `lsfg-vk-framegen`. This
  module currently links neither those sources nor their deps (pe-parse, dxbc, toml11).
- `Extract` finds Lossless.dll via `Config::dll`, set either from a TOML config file or the
  `LSFG_DLL_PATH` env var (`src/config/config.cpp`). Android has no equivalent default config
  path, so our glue needs to `setenv("LSFG_DLL_PATH", ...)` itself before calling
  `extractShaders()`.
- That env var needs a real filesystem path. `FrameGenDllPicker` currently only stores a
  `content://` URI from the system picker — native code can't read that directly, so the
  picker will need to copy the file into app-internal storage before any of this works.

## What's real and working (app-side, not the engine itself)
- `AllSettings.frameGenerationEnabled` / `frameGenerationDllUri` — persisted settings.
- Settings screen toggle + tier warning, wired into `RendererSettingsScreen`.
- GPU detection (`GpuTierDetector`) off the benchmark screen's GL context.
- `VulkanCapabilities.supportsFrameGeneration` — real `VK_EXT_robustness2` check, lsfg-vk's
  actual hardware requirement.
- DLL picker (SAF) — stores a URI only, still needs the internal-storage-copy step above.
- `native/lsfg-vk-android` submodule + `:framegen` CMake module, now producing
  `libzalith_framegen_jni.so` successfully as of the target-name fix.

## Still stubbed / unimplemented
- `nativeInitialize` — always returns false. Needs the extract.cpp/trans.cpp linking above,
  a real Vulkan `deviceUUID`, and the internal-storage DLL copy.
- `nativeCreateContext` / `nativePresent` — need real `AHardwareBuffer`s from Minecraft's
  actual rendered frames. Nothing produces those yet (see next section).

## The open question nothing here answers
lsfg-vk on Linux works by intercepting `vkQueuePresentKHR` as a Vulkan layer. Zalith doesn't
need LSFG-Android's MediaProjection/overlay workaround (that only exists because Android
won't let one app hook another app's swapchain) — Zalith renders Minecraft itself, in the
same process. But it still needs *some* interception point that hands this code the two most
recently rendered frames before they hit the display, and only the Kopper/Vulkan-Zink
renderer path has a real Vulkan swapchain to hook — GL4ES/VirGL/MobileGlues have nothing to
intercept here.

`ctxbridges/bridge_tbl.h` already does the GL equivalent of this (`br_swap_buffers` as a
function-pointer table intercepting `eglSwapBuffers`). There's no Vulkan equivalent yet. The
most likely shape of one, based on how `driver_helper.c` already redirects Vulkan driver
loading: a shim that LWJGL's `dlopen("libvulkan.so")` resolves to instead of the real loader,
forwarding every function except `vkQueuePresentKHR`.

That shim doesn't exist. Confirming this approach is even viable on-device should happen
before more code gets written against it.

## Licensing note
`native/lsfg-vk-android` is MIT (see its `LICENSE.md`). It does not bundle, redistribute, or
download `Lossless.dll` — the DLL picker only ever stores a path/URI the user selects to a
copy they already own.
