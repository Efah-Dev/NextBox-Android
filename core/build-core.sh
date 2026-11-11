#!/usr/bin/env bash
# Builds app/libs/libbox.aar on Linux / macOS (the counterpart of
# build-core.ps1): sing-box libbox + nbcore + nbtest in one gomobile bind.
#
#   core/build-core.sh                  # arm64 + armv7 + x86_64
#   core/build-core.sh android/arm64
#
# Needs Go, gomobile + gobind (github.com/sagernet/gomobile v0.1.12),
# ANDROID_HOME with a platform, ANDROID_NDK_HOME and a JDK.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
targets="${1:-android/arm64,android/arm,android/amd64}"

export PATH="$(go env GOPATH)/bin:$PATH"
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi

# gomobile only recognises "platforms/android-NN"; newer SDKs name the folder
# "android-37.0". Point it at a shim SDK with a symlink in that case.
if ! ls -d "$ANDROID_HOME"/platforms/android-[0-9]* 2>/dev/null | grep -Eq 'android-[0-9]+$'; then
  real="$(ls -d "$ANDROID_HOME"/platforms/android-*.* | sort -V | tail -1)"
  level="$(basename "$real" | sed -E 's/android-([0-9]+).*/\1/')"
  shim="${TMPDIR:-/tmp}/nextbox-android-sdk"
  mkdir -p "$shim/platforms"
  ln -sfn "$real" "$shim/platforms/android-$level"
  export ANDROID_HOME="$shim"
fi

tags="with_gvisor,with_quic,with_utls,badlinkname,tfogo_checklinkname0,ts_omit_logtail,ts_omit_ssh,ts_omit_drive,ts_omit_taildrop,ts_omit_webclient,ts_omit_doctor,ts_omit_capture,ts_omit_kube,ts_omit_aws,ts_omit_synology,ts_omit_bird"
version="1.14.4"
ldflags="-X github.com/sagernet/sing-box/constant.Version=$version -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -checklinkname=0 -s -w -buildid="

mkdir -p "$here/../app/libs"
cd "$here"
rm -rf build
go mod tidy
gomobile bind -v -o ../app/libs/libbox.aar -target "$targets" -androidapi 29 \
  -javapkg=dev.efah.nextbox -libname=box \
  -trimpath -buildvcs=false -ldflags "$ldflags" -tags "$tags" \
  github.com/sagernet/sing-box/experimental/libbox nextbox/mobile nextbox-android/core/nbtest
rm -rf build
ls -lh ../app/libs/libbox.aar
