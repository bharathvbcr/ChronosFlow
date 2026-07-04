#!/usr/bin/env bash
# Build the ChronosFlow iOS app. Requires macOS + Xcode 27 (iOS 27 SDK).
#
# Usage:
#   ./build.sh            # build the app for the iOS Simulator
#   ./build.sh widgets    # regenerate the project (app + iOS/watch widgets + watch app), then build
#   ./build.sh watch      # build the watchOS app (+ its widget extension) for the watch Simulator
#   ./build.sh device     # build for a generic iOS device (requires signing)
#   ./build.sh core       # build + test the portable ChronosCore package (works on any OS)
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-app}"
SCHEME="ChronosFlow"
# Prefer the newest Pro simulator when present; fall back to generic destination when none match
# (CI / Xcode version skew — e.g. Xcode 26 simulators vs iOS 27 deployment target).
SIM_DEVICE="${SIM_DEVICE:-iPhone 17 Pro}"
WATCH_SIM_DEVICE="${WATCH_SIM_DEVICE:-Apple Watch Series 11 (46mm)}"

# The portable core builds on any OS with a Swift toolchain — no Xcode required.
if [[ "$MODE" == "core" ]]; then
  echo "==> Building + testing the portable ChronosCore package"
  cd ChronosCore
  swift build
  swift test
  echo "==> ChronosCore OK"
  exit 0
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "error: xcodebuild not found. The full app must be built on macOS with Xcode 27 (iOS 27 SDK)." >&2
  echo "       The platform-agnostic logic core can be built here with: ./build.sh core" >&2
  exit 1
fi

if [[ "$MODE" == "widgets" || "$MODE" == "watch" ]]; then
  if ! command -v xcodegen >/dev/null 2>&1; then
    echo "Installing XcodeGen (brew install xcodegen) is required to (re)generate the extension/watch targets." >&2
    echo "Run: brew install xcodegen" >&2
    exit 1
  fi
  echo "==> Regenerating ChronosFlow.xcodeproj (app + iOS widgets + watch app + watch widgets)"
  xcodegen generate
fi

if [[ "$MODE" == "watch" ]]; then
  echo "==> Building ChronosWatch for the watchOS Simulator ($WATCH_SIM_DEVICE)"
  xcodebuild -project ChronosFlow.xcodeproj -scheme "ChronosWatch" \
    -destination "platform=watchOS Simulator,name=$WATCH_SIM_DEVICE" -configuration Debug \
    CODE_SIGNING_ALLOWED=NO build
elif [[ "$MODE" == "device" ]]; then
  echo "==> Building $SCHEME for a generic iOS device"
  xcodebuild -project ChronosFlow.xcodeproj -scheme "$SCHEME" \
    -destination 'generic/platform=iOS' -configuration Debug \
    build
else
  DEST="generic/platform=iOS Simulator"
  if xcodebuild -project ChronosFlow.xcodeproj -scheme "$SCHEME" -showdestinations 2>/dev/null \
       | grep -Fq "name:$SIM_DEVICE"; then
    DEST="platform=iOS Simulator,name=$SIM_DEVICE"
  else
    echo "warning: no runnable destination for '$SIM_DEVICE' (deployment target may exceed installed runtimes); using generic/platform=iOS Simulator" >&2
  fi
  echo "==> Building $SCHEME ($DEST)"
  xcodebuild -project ChronosFlow.xcodeproj -scheme "$SCHEME" \
    -destination "$DEST" -configuration Debug \
    CODE_SIGNING_ALLOWED=NO ONLY_ACTIVE_ARCH=YES ARCHS=arm64 \
    build
fi

echo "==> Build succeeded"
