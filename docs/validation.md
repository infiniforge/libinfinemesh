# Validation

Checked 2026-11-18, JDK 21 / Android SDK 36 / build tools 35.0.0, Termux aapt2.

- `./build.sh --offline --max-workers=1 --console=plain`: core tests and AAR passed.
- `:mesh-android:lintDebug`: passed with no reported issues.
- `./gradlew :mesh-core:test -PmeshCoreOnly --offline --max-workers=1 --console=plain`:
  passed; no SDK-dependent Android module is configured in this mode.
- 17 JUnit tests passed, including malformed and bounded input, normals, STL header
  ambiguity, G-code modes/offsets/units/arcs/dwell, and extension registration.
- `python3 scripts/check-shaders.py`: actual renderer shaders compile as GLSL ES 1.00.
- Consumer APK assembled and passed lint (only its target SDK advisory remains).
- Consumer samples (cube: 12 triangles, spiral: 2,163 segments) and local STLs
  (2,996 and 2,156 triangles) decoded; both normal modes built successfully.

No Android device was connected. Rendering, gesture interaction and lifecycle
acceptance on actual devices remain to be checked by a host application.

## 0.1.1 depth regression

The renderer now defaults to flat shading, prefers 24-bit depth, and fits the
near/far clip planes to normalized geometry and the visible grid. Three camera
JVM tests cover separation of surfaces 0.0001 normalized units apart, scene
containment across zoom/aspect changes, and a usable close-up near plane.

Optional native host regression (JDK, C compiler, EGL/GLES2 development libraries):

```sh
python3 scripts/check-depth-rendering.py
```

This compiles the actual viewer shaders and calls the actual ClipPlanes helper.
A 32px offscreen render places red geometry 0.0001 units in front of blue geometry.
It reproduces the old 16-bit depth collision, then checks correct front-face
visibility with fitted 16-bit and 24-bit depth buffers. This complements, rather
than replaces, acceptance on the handset's EGL driver.
