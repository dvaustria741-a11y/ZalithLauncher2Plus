# Frame Generation — integration status

Scaffolding only. Nothing here has been built or run — there's no NDK/Vulkan environment in
the sandbox that wrote this, so treat every claim below as "should be right based on reading
the source," not "verified."

## What's real and working
- `AllSettings.frameGenerationEnabled` / `frameGenerationDllUri` — persisted settings.
- Settings screen toggle (`FrameGenerationSetting.kt`) + tier warning, now actually wired
  into `RendererSettingsScreen`.
- GPU detection (`GpuTierDetector`) runs off the benchmark screen's GL context.
- `VulkanCapabilities.supportsFrameGeneration` — checks for the real `VK_EXT_robustness2`
  requirement lsfg-vk-android hard-codes, more accurate than the GPU-model-name guess.
- DLL picker (`FrameGenDllPicker.kt`) — stores a content URI to the user's own Lossless.dll
  via Storage Access Framework, with persisted read permission. Doesn't parse or touch the
  file's contents; that's lsfg-vk-android's job once wired up.
- `native/lsfg-vk-android` submodule + a separate `:framegen` Gradle module (CMake, since
  `:ZalithLauncher` already owns `ndkBuild` and AGP only allows one native build system per
  module) that should build it into `libzalith_framegen_jni.so`.

## What's stubbed (see TODOs in `frame_gen_jni.cpp`)
- `nativeCreateContext` doesn't call `LSFG_3_1::createContextFromAHB` yet — the real
  function signature needs checking against the actual header once this builds, not guessed
  from the README.
- `nativeGenerate` is a no-op. This is the actual frame hand-off and it's the hard part.
- The CMake target name (`framegen`) linked in `CMakeLists.txt` is a guess — confirm it
  against `cmake --build . --target help` once this builds locally.

## The open question nothing here answers
lsfg-vk on Linux works by intercepting `vkQueuePresentKHR` as a Vulkan layer. Zalith doesn't
need LSFG-Android's MediaProjection/overlay workaround (that only exists because Android
won't let one app hook another app's swapchain) — Zalith renders Minecraft itself, in the
same process. But it still needs *some* interception point that hands this code the two most
recently rendered frames as `AHardwareBuffer`s before they hit the display, and only the
Kopper/Vulkan-Zink renderer path has a real Vulkan swapchain to hook — GL4ES/VirGL/MobileGlues
have nothing to intercept here.

`ctxbridges/bridge_tbl.h` already does the GL equivalent of this (`br_swap_buffers` as a
function-pointer table intercepting `eglSwapBuffers`). There's no Vulkan equivalent yet. The
most likely shape of one, based on how `driver_helper.c` already redirects Vulkan driver
loading: a shim that LWJGL's `dlopen("libvulkan.so")` resolves to instead of the real loader,
forwarding every function except `vkQueuePresentKHR`.

That shim doesn't exist. Confirming this approach is even viable on-device — Android may
impose restrictions here that don't show up from reading source — should happen before more
code gets written against it.

## Licensing note
`native/lsfg-vk-android` is MIT (see its `LICENSE.md`). It does not bundle, redistribute, or
download `Lossless.dll` — the DLL picker above only ever stores a path the user selects to a
copy they already own. Nothing in this scaffold touches that file's contents.
