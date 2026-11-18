# libinfinemesh development

Read README.md, docs/API.md and docs/ECOSYSTEM.md before changing public contracts.
Keep mesh-core free of Android and application dependencies. Host apps own URI
loading, lifecycle, controls, preview clocks and settings; mesh-android supplies
reusable rendering and gestures. Document decoder dialects and resource limits.
Ignore unknown G-code commands as requested by the user, keep a warning, and
continue supported simulation. Malformed supported commands still report errors.
Keep all functionality read-only. New format support belongs behind public decoder
APIs; texture support will require a documented material/resource contract.
Run ./build.sh for core tests and the Android AAR; run JVM-only tests with
-PmeshCoreOnly. Generated ecosystem docs come from the central manifest.
