#!/bin/bash
# Builds the overlay app (overlay/) into out/MicrowaveOverlay.apk without Gradle: aapt2, javac, d8,
# zipalign and apksigner from the Android SDK.
#
#   OVERLAY_KEYSTORE=/path/to/key.jks OVERLAY_KEYSTORE_PASS=... overlay/build.sh
#
# Needs the Android SDK (ANDROID_SDK_ROOT, build-tools and a platform), a JDK and python3. The
# keystore is not in the repository: updates only install over an app signed with the same key.
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -n 1)
JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -n 1)/android.jar
VERSION_NAME=1.0
VERSION_CODE=1
: "${OVERLAY_KEYSTORE:?set OVERLAY_KEYSTORE to the signing keystore}"
: "${OVERLAY_KEYSTORE_PASS:?set OVERLAY_KEYSTORE_PASS}"

B=build
rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex" ../out

"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  --min-sdk-version 34 --target-sdk-version 35 --version-code $VERSION_CODE --version-name $VERSION_NAME "$B/res.zip"
javac -nowarn -source 11 -target 11 -cp "$JAR" -d "$B/classes" $(find src "$B/gen" -name '*.java')
"$BT/d8.bat" --lib "$JAR" --min-api 34 --output "$B/dex" $(find "$B/classes" -name '*.class')
python3 - "$B/base.apk" "$B/dex/classes.dex" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(sys.argv[2], 'classes.dex')
PY
"$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"
"$BT/apksigner.bat" sign --ks "$OVERLAY_KEYSTORE" --ks-pass "pass:$OVERLAY_KEYSTORE_PASS" \
  --out ../out/MicrowaveOverlay.apk "$B/aligned.apk"
"$BT/apksigner.bat" verify ../out/MicrowaveOverlay.apk
ls -la ../out/MicrowaveOverlay.apk
