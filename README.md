# libinfinemesh

Reusable read-only 3D model and G-code preview library, version **0.2.0**.

- `mesh-core`: Java 17 immutable geometry, bounds, flat/Gouraud normals, ASCII/binary
  STL, Cartesian single-extruder G-code toolpaths, timing and decoder registry.
- `mesh-android`: GLES 2.0 Android MeshView with gestures, wireframe, flat and
  Gouraud shading (flat by default), ground grid and timed travel/extrusion rendering.

No application dependency, storage permissions or printer connection. Materials,
texture mapping, other model formats and full G-code dialect coverage are future
work. See [API and supported subsets](docs/API.md).

## Standalone build

Git, JDK 17 or 21, Android SDK platform 36/build tools 35.0.0 and a populated cache
or network access for Gradle/Maven are required. The wrapper pins Gradle 9.7.1 by
SHA-256; Android modules use AGP 8.9.2. Runtime minimum: Android API 26, GLES 2.0.

```sh
export ANDROID_HOME=/path/to/android-sdk
./build.sh --max-workers=1 --console=plain
```

This runs core and camera-depth JVM tests plus Android lint and builds the Android AAR. `local.properties` with
`sdk.dir` is also supported; Termux uses installed `aapt2`. No application or sibling
checkout is needed. For JVM-only builds without an SDK, run
`./gradlew :mesh-core:test -PmeshCoreOnly --max-workers=1 --console=plain`.
Build output: `mesh-core/build/libs/mesh-core-0.2.0.jar` when running `:mesh-core:jar`,
and `mesh-android/build/outputs/aar/mesh-android-debug.aar`. Source: [infiniforge/libinfinemesh](https://github.com/infiniforge/libinfinemesh).

Consume the included build with coordinates
`io.github.gretchycat.libinfinemesh:mesh-android:0.2.0` or `mesh-core:0.2.0`.
[Maven artifacts are not published yet.](docs/API.md)
[Documentation](docs/README.md) · [MIT license](LICENSE.md)

Thumbnails use `ModelThumbnail.render(document, pixels)` in mesh-core (transparent
ARGB, flat faces, depth buffer, full toolpath). Android consumers discover STL/G-code
PNG thumbnails through `MeshCapabilityProvider` and libinfinistream's
`EncodedImageExtractor`. Keep the service entry when merging Java resources.
Thumbnail decode limits are 16 MiB / 100,000 triangles / 200,000 segments; output is
at most 512 pixels with a 24-million-sample raster budget. Oversized or malformed
models fall back to the host's normal type icon. No source file is modified.

The Android module now links pinned libinfinistream source. `build.sh` fetches and
checks `dependencies.lock.json`; composite hosts supply their own flat, pinned
`infini.dependencies` directory. JVM-only builds still need no Android or stream dependency.
