# Android Product Audit

Audit scope: `android-only-hardening`. This document distinguishes runtime code shipped in the Android APK from build, AOT-generation, validation, and desktop-development code that remains in the repository.

## Product boundary and targets

The APK is produced by `ports/android/app` (Gradle), not by running the desktop launcher. Its native build inputs are staged separately and constrained to `arm64-v8a`. The app module's `checkNativeStage` task requires the following shared objects under the ABI directory before packaging:

- `libTriAevum.so` — Android native host and runtime.
- `libtriaevum_title_bootstrap.so` — title bootstrap.
- `libtriaevum_title_aot.so` — translated whole-AOT title module.
- `libc++_shared.so` — Android C++ runtime.

This packaging boundary matters: desktop executables and test binaries produced by CMake are not copied into the APK by Gradle. The product package uses the staged shared-library directory, not the entire CMake build tree. Gradle now rejects any staged ABI other than `arm64-v8a` and any shared-library payload other than the four allowlisted libraries. The staging script recreates that ABI folder before copying, so stale desktop/debug objects cannot silently survive a restage.

## Desktop-only code and how it is isolated

The following source areas are useful to desktop builds but are not Android product APIs:

- SDL window/event integration (`gfx_sdl2.cpp` and desktop SDL window adapters).
- Windows-specific DXGI/Direct3D 11 paths and the Windows-only D3D12/NGX provider.
- Apple Metal and Objective-C++ paths.
- Desktop NRI upscaler SDK integrations (NIS/FSR/NGX providers).

The current CMake configuration removes `gfx_dxgi`/`gfx_direct3d*` sources outside Windows, removes `gfx_metal*` outside Darwin/iOS, removes `gfx_sdl2*` on Android, and includes `gfx_android*` only on Android. It excludes `gfx_opengl*` from Android/iOS. Windows-only targets/dependencies are platform-gated, NRI upscaler SDKs are enabled only when `NOT ANDROID`, and D3D12/NGX is only enabled for Windows configurations. The Android native build enters the shared runtime through `ports/android/runtime/CMakeLists.txt`; it does not package the desktop launcher or desktop runtime DLLs/frameworks.

These backend files should **not** be deleted indiscriminately from the shared source tree: they serve other supported build configurations, and shared PICA/runtime contracts are referenced by AOT generation and validation. Product isolation is achieved through target selection, platform guards, and strict native-library staging. The verified filters live in `runtime/three_ds_recomp/src/fast/CMakeLists.txt`; platform audio selection is in `runtime/three_ds_recomp/src/ship/CMakeLists.txt`. Those source lists, rather than deleting files from the repository, are the correct boundary for preventing desktop implementations from entering the Android runtime.

## AOT and validation tools intentionally retained

The following are development/publisher tools rather than APK payload:

- `scripts/build-android-native.sh`: cross-builds Android runtime components, module services, probes and service tests; optional title compilation is controlled by `TRIAEVUM_BUILD_TITLE=1`.
- `scripts/build-android-renderer.sh`: cross-builds and links the real renderer and a link/CPU-shader probe.
- `tools/android/stage_native_app.py`: stages the exact ARM64 shared objects consumed by Gradle.
- `.github/workflows/android-release.yml`: checks the AOT source inventory and shutdown API before native/Gradle packaging. It permits a placeholder AOT module only for non-publishing validation and fails closed for publishable builds unless a real translated title module is produced.
- `tools/oot3d/**` AOT/runtime sources and tests, and `tools/triaevum_release/test_shutdown_request.py`: title, ABI, service, renderer-contract and shutdown validation.

CMake test executables marked `EXCLUDE_FROM_ALL` are not automatically part of the Android app target. The native build script deliberately selects specific validation targets. Keep those tools: deleting them would remove the means to produce or verify the AOT title and Android native interfaces.

## Android host fixes in this branch

- `ANativeWindow_fromSurface` returns an owned reference. `NotifySurfaceCreated/Changed` adopts that reference and releases duplicates when the same native window is reported repeatedly; the compatibility setter accepts a borrowed pointer and acquires its own reference. Old windows are retained while the renderer may still own Vulkan surface/swapchain resources. After the renderer waits for outstanding presents and device idle, then successfully destroys/recreates its `VkSurfaceKHR`, it releases retired-window references. If no replacement surface becomes available, the final native-runtime teardown releases them instead.
- Surface destruction wakes the render wait before waiting briefly for a frame-boundary acknowledgement. A timeout is logged as a timeout; it is no longer described as a guaranteed safe detach.
- `nativePrepareSession` clears the shared shutdown request and resets the host's stop flag for a fresh native session.
- `nativeRequestShutdown` requests normal-loop termination and wakes any wait in `IsFrameReady`.
- Activity teardown releases virtual inputs, requests shutdown, and joins the native game thread before returning to Android's superclass lifecycle. Native-window references are released only after the game entrypoint has returned and its local renderer/context teardown has completed. The join intentionally has no timeout: abandoning a still-running renderer thread would be unsafe, but a blocked driver/shutdown path could stall the UI or trigger an ANR. This requires device testing and should be replaced only with a stronger native lifecycle handshake, not a process kill.
- A native launch exception now returns a defined status code rather than falling off an `int` function.
- ROM-folder access uses `ACTION_OPEN_DOCUMENT_TREE` with a persisted read grant. The app no longer requests `MANAGE_EXTERNAL_STORAGE`, legacy read/write storage permissions, or legacy global-storage mode. It copies the selected files into its app-specific data directory. The app manifest also drops unused Internet/network-state permissions and the cleartext-traffic opt-in; no HTTP/socket client is present in the Android app source.
- Shared Android input bits use atomic read-modify-write operations, and storage-path access returns a mutex-protected value rather than a reference whose lock has already been released.

## Renderer finding: OpenGL ES is not implemented as the Android game backend

The Android runtime currently forces `OOT3D_ENABLE_VULKAN_RENDERER=ON` and the Android renderer CMake project forces Vulkan/NRI on. `gfx_android.cpp` reports `UsesVulkan() == true`, and its GUI/window initialization selects the Vulkan backend. `tools/oot3d/native_game_runtime/oot3d_native_a32_window.cpp` directly constructs PICA Vulkan draw plans and records Vulkan-oriented PICA execution telemetry.

There are generic OpenGL/GLES code paths in `gfx_opengl.cpp`, but CMake currently excludes `backends/gfx_opengl.*` from the Android runtime target. Those sources are not even part of the built Android backend today, and that alone is not an Android game renderer. The existing PICA OpenGL implementation (`gfx_opengl_pica.cpp`) explicitly rejects the `USE_OPENGLES` path in operations that require OpenGL 4.3, and no EGL context/surface lifecycle is wired into the current Android window backend. Switching off Vulkan would therefore leave the current AOT PICA renderer without a functioning render/present path.

A real GLES backend is a separate renderer deliverable, not a build-flag edit. It must integrate Android EGL lifecycle; implement or port PICA command/pipeline mapping, shaders compatible with the target GLES version, vertex/index data, texture upload/sampling, render targets/depth, display transfer/composition and UI presentation; preserve synchronization and frame pacing; and have backend-specific correctness tests before device qualification. The existing PICA semantic/decode logic, AOT shader inventories and platform-independent policy code can be reused where they are API-independent, but Vulkan/NRI resource owners, barriers, swapchain code and draw plans cannot be considered GLES-compatible by assumption.

## Validation status and acceptance gate

This branch was edited through the GitHub API. No Android NDK build, Gradle APK build, test executable, deployment or physical-device run was performed in this session. The PR remains a draft until remote checks and build results are reviewed.

Required acceptance checks, in order:

1. Android NDK configure/build for the native runtime and title bootstrap/AOT module; Gradle debug APK packaging with exactly the staged ARM64 shared libraries.
2. Native unit/service tests and AOT manifest/plugin verification.
3. APK installation and cold/warm launch; surface create/change/destroy, rotate/background/resume, back/close and repeat-launch tests; check that shutdown joins and native-window references balance.
4. Verify controller/touch input release, TopScreen UI, storage import and save persistence, audio output, and crash-free gameplay.
5. For the requested POCO M7 / Adreno 610, test startup, rendered frames, GPU/driver stability and sustained frame pacing while capturing filtered logcat and device/build identifiers. An Adreno 830 probe or a successful cross-link is not evidence for this device.

No ROM, game image, or proprietary game assets are included by this audit. Existing GPL-3.0-or-later terms and upstream copyright notices must be preserved.
