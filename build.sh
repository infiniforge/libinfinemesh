#!/usr/bin/env sh
set -eu
cd -- "$(dirname -- "$0")"
python3 scripts/fetch-dependencies.py
python3 scripts/fetch-dependencies.py --check
MESH_SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$MESH_SDK" ] && [ -f local.properties ]; then
    MESH_SDK=$(sed -n 's/^[[:space:]]*sdk.dir[[:space:]]*[:=][[:space:]]*//p' local.properties | head -n 1)
fi
if [ -z "$MESH_SDK" ]; then
    for MESH_CANDIDATE in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
        if [ -d "$MESH_CANDIDATE/platforms" ]; then MESH_SDK=$MESH_CANDIDATE; break; fi
    done
fi
if [ -n "$MESH_SDK" ]; then set -- "-Dandroid.home=$MESH_SDK" "$@"; fi
if [ -n "${TERMUX_VERSION:-}" ]; then
    MESH_AAPT2=$(command -v aapt2 || true)
    if [ -z "$MESH_AAPT2" ]; then echo "Install aapt2 with: pkg install aapt2" >&2; exit 1; fi
    set -- "-Pandroid.aapt2FromMavenOverride=$MESH_AAPT2" "$@"
fi
./gradlew :mesh-core:test :mesh-android:testDebugUnitTest :mesh-android:assembleDebug :mesh-android:lintDebug "$@"
