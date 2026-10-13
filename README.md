# OOT-3DS-ANDROID

Android-focused ARM64 port of the TriAevum AOT recompilation of *The Legend of Zelda: Ocarina of Time 3D*.

This repository is intended to become an Android-first project. The shared AOT title/runtime and PICA rendering code are retained where the Android port depends on them; desktop packaging and desktop-only front ends are not part of the Android product.

## Current status

**Experimental developer foundation — not a stable or fully playable release.**

The current Android implementation has a Gradle application module, an Android controls library, and a native ARM64 build path. The native foundation currently targets **Vulkan/NRI**, not OpenGL ES. Existing documentation reports limited intro bring-up on an Adreno 830; this is not evidence of full gameplay, reliable input, or compatibility with other GPUs.

The following remain separate milestones and must not be represented as complete merely because a library links:

- playable boot and gameplay on a physical Android device;
- Android lifecycle and surface recreation under pause/resume;
- touch/controller integration and single-screen TopScreen coordinate mapping;
- user-owned game-data import through Android's document picker, without bundling game data;
- save persistence, audio-device handling, and graceful error reporting;
- performance and frame-pacing validation on the target GPU;
- any OpenGL ES backend or Vulkan-to-OpenGL migration.

## Android development

See [Android native foundation](ports/android/README.md) for the qualified toolchain, native build scripts, and current limitations.

The native build and the APK packaging step are intentionally separate. `scripts/build-android-native.sh` builds native components; it explicitly does **not** produce a playable APK. The app's Gradle configuration requires a staged set of ARM64 shared libraries.

Build the native foundation on Linux with the Android NDK as described in the Android documentation. Do not infer that the APK is playable from a successful CMake or Gradle build alone.

## Android-only project policy

1. **Android is the product target.** New user-facing features, packaging, input, storage, audio, lifecycle, and graphics integration must be designed for Android.
2. **Retain shared engine code.** Do not delete AOT translation, runtime, PICA renderer, plugin loader, or native probes just because they originated in desktop builds. First prove they are not required by the Android target.
3. **Remove desktop-only product surface incrementally.** Windows/Linux installers, desktop GUI code, and desktop release workflows may be removed from the Android product path only after checking all references, build targets, scripts, and licensing obligations. Development tools required to generate or validate Android inputs may remain.
4. **No fake backend switch.** Disabling Vulkan is not an OpenGL ES implementation. A GLES backend requires a real implementation of the renderer's resource, shader, synchronization, and presentation contracts.
5. **Preserve TopScreen.** The Android presentation must use a single gameplay surface and transform input coordinates consistently with scaling and letterboxing. Do not change guest/game logic to implement host UI behavior.
6. **Keep simulation timing deterministic.** Frame interpolation belongs to presentation; it must not advance gameplay state, collision, or input more than the original simulation expects.
7. **No copyrighted game data in Git or APKs.** Do not add ROMs, extracted Nintendo assets, proprietary shaders/textures, or generated game-data packages. Any supported import workflow must use data supplied by the user and must respect applicable law.
8. **Preserve license and provenance.** Keep GPL-3.0-or-later notices and all third-party licenses, copyright headers, and provenance records for retained components.

## Architecture direction

The intended platform boundary is:

- **Shared native core:** AOT title, runtime services, PICA semantics, input ABI, save/state logic.
- **Android host:** Activity/lifecycle, native surface, document picker/import, app-private storage, audio focus/device binding, Android input and error presentation.
- **Renderer backend:** explicit backend interface; Vulkan/NRI remains the current implementation until a GLES implementation is complete and tested.
- **Presentation:** one TopScreen viewport, device-aware scaling/letterboxing, optional frame interpolation with independent simulation and presentation clocks.

Do not introduce a second game/runtime implementation in Kotlin. Kotlin should own Android UI and platform APIs; C/C++ should own the native engine and renderer.

## Validation requirements

Before calling a change complete:

- run the relevant native unit tests and source-manifest checks;
- build the Android ARM64 targets with the documented NDK/CMake/Ninja versions;
- build/package the APK with the staged native libraries;
- test launch, pause/resume, surface recreation, touch cancellation, controller disconnect, import failure, save/reload, and shutdown;
- record device model, Android version, GPU/driver, renderer backend, resolution, simulation rate, presented FPS, and logs for performance claims.

A passing host test or successful link is useful evidence, but is not a substitute for on-device validation.

## Credits and licenses

This project derives from [TriAevum](https://github.com/coccofresco/TriAevum) and retains components from libultraship, Azahar, NVIDIA NRI, and other upstream projects. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [LICENSE_SCOPE.md](LICENSE_SCOPE.md), and the license files in each imported component. Preserve all attribution and license requirements when modifying or removing code.

This is an independent community project and is not affiliated with or endorsed by Nintendo.
