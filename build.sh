#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

sdk_root="${ANDROID_HOME:-$HOME/Android/Sdk}"
jdk_root="${JAVA_HOME:-/opt/android-studio/jbr}"
export JAVA_HOME="$jdk_root"
export PATH="$jdk_root/bin:$PATH"
android_jar="$sdk_root/platforms/android-35/android.jar"
compile_jar="$sdk_root/platforms/android-18/android.jar"
build_tools="$sdk_root/build-tools/36.0.0"
test -f "$android_jar"
test -f "$compile_jar"
test -x "$build_tools/aapt"
mkdir -p build/classes build/dex build/gen dist

"$build_tools/aapt" package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I "$android_jar" \
  --min-sdk-version 18 --target-sdk-version 18 \
  -F build/base.apk
javac -source 8 -target 8 -cp "$compile_jar:libs/zxing-core-3.3.2.jar" -d build/classes \
  $(find src -name '*.java' -print) \
  $(find build/gen -name '*.java' -print)
"$build_tools/d8" --min-api 18 --lib "$android_jar" --output build/dex \
  $(find build/classes -name '*.class' -print) libs/zxing-core-3.3.2.jar
(cd build/dex && "$build_tools/aapt" add ../base.apk classes.dex)
"$build_tools/zipalign" -f 4 build/base.apk build/aligned.apk

if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -noprompt -keystore build/debug.keystore \
    -alias indiehouse -storepass android -keypass android \
    -dname 'CN=Indie House local test' -keyalg RSA -keysize 2048 -validity 3650
fi
"$build_tools/apksigner" sign --ks build/debug.keystore --ks-key-alias indiehouse \
  --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled false \
  --v3-signing-enabled false \
  --out dist/indie-house-leap-api18.apk build/aligned.apk
"$build_tools/apksigner" verify --min-sdk-version 18 --verbose \
  dist/indie-house-leap-api18.apk
