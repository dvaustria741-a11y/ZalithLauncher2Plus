/*
 * Zalith Launcher 2 Plus
 *
 * Separate module from :ZalithLauncher on purpose: an AGP module can only declare one
 * externalNativeBuild system, and :ZalithLauncher already owns ndkBuild (src/main/jni/Android.mk).
 * lsfg-vk-android ships a CMake build, so it gets its own module here and :ZalithLauncher
 * depends on it — AGP merges the resulting .so into the final APK's jniLibs regardless of
 * which native build system produced it, the same way the prebuilt .so files already sitting
 * in ZalithLauncher/src/main/jniLibs/ get merged in.
 */

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.movtery.zalithlauncher.framegen"
    compileSdk = 35

    defaultConfig {
        minSdk = 28 // AHardwareBuffer + VK_ANDROID_external_memory_android_hardware_buffer need API 28+

        ndk {
            // VK_EXT_robustness2 in practice only shows up on Adreno 7xx / recent Mali —
            // arm64-v8a only, no point building this for armeabi-v7a/x86/x86_64.
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
