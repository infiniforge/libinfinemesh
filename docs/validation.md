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
