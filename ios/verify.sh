#!/usr/bin/env bash
# ChronosFlow iOS — full Mac-side verification in ONE command.
#
# Runs the entire "remaining" verification that cannot be done on Windows:
#   1. ChronosCore  : swift build + swift test (the portable logic core)
#   2. xcodegen     : regenerate the project incl. ALL targets (app, widgets, watch, watch-widgets)
#   3. xcodebuild   : compile the iOS app (which embeds the widget + watch app + watch widgets)
#   4. xcodebuild   : compile the watchOS app for a watchOS Simulator (separate platform)
#   5. smoke run    : boot an iOS Simulator, install + launch the app, capture a screenshot
#                     (on-device verification that the app actually starts)
#
# Requires macOS + Xcode 27 (iOS 27 SDK) and XcodeGen (`brew install xcodegen`).
# Usage:
#   ./verify.sh            # full: build all targets + ChronosCore tests + simulator smoke
#   ./verify.sh --no-smoke # build + tests only (skip booting a simulator)
#   IOS_SIM="iPhone 17 Pro" WATCH_SIM="Apple Watch Series 11 (46mm)" ./verify.sh   # pin devices
#
# Exit code is non-zero if any step fails; a PASS/FAIL summary prints at the end.
set -uo pipefail
cd "$(dirname "$0")"

SMOKE=1
[[ "${1:-}" == "--no-smoke" ]] && SMOKE=0

PROJECT="ChronosFlow.xcodeproj"
APP_SCHEME="ChronosFlow"
WATCH_SCHEME="ChronosWatch"
APP_BUNDLE_ID="com.chronosflow.app"
DD="$PWD/build/DerivedData"

# --- result tracking -------------------------------------------------------
declare -a STEPS=() RESULTS=()
record() { STEPS+=("$1"); RESULTS+=("$2"); }
have() { command -v "$1" >/dev/null 2>&1; }

red()  { printf "\033[31m%s\033[0m\n" "$*"; }
grn()  { printf "\033[32m%s\033[0m\n" "$*"; }
hdr()  { printf "\n\033[1m==> %s\033[0m\n" "$*"; }

# --- prerequisites ---------------------------------------------------------
hdr "Checking prerequisites"
if ! have xcodebuild; then
  red "xcodebuild not found. This script must run on macOS with Xcode 27 (iOS 27 SDK)."
  red "The portable core can still be tested anywhere with: ./build.sh core"
  exit 1
fi
xcodebuild -version | head -1
if ! have xcodegen; then
  red "XcodeGen not found. Install it: brew install xcodegen"
  exit 1
fi
PRETTY=cat; have xcbeautify && PRETTY=xcbeautify

# --- 1. ChronosCore ---------------------------------------------------------
hdr "1/5  ChronosCore — swift build + test (portable logic core)"
if ( cd ChronosCore && swift build && swift test ); then
  record "ChronosCore build + test" PASS
else
  record "ChronosCore build + test" FAIL
fi

# --- 2. XcodeGen ------------------------------------------------------------
hdr "2/5  XcodeGen — regenerate project (app + widgets + watch + watch-widgets)"
if xcodegen generate; then record "xcodegen generate" PASS; else record "xcodegen generate" FAIL; fi

# --- destination discovery --------------------------------------------------
pick_sim() { # $1 = grep pattern for device line
  xcrun simctl list devices available | grep -m1 "$1" | sed -E 's/.*\(([0-9A-Fa-f-]{36})\).*/\1/'
}
IOS_NAME="${IOS_SIM:-}"; WATCH_NAME="${WATCH_SIM:-}"
IOS_UDID=$( [[ -n "$IOS_NAME" ]] && pick_sim "$IOS_NAME" || pick_sim "iPhone" )
WATCH_UDID=$( [[ -n "$WATCH_NAME" ]] && pick_sim "$WATCH_NAME" || pick_sim "Apple Watch" )
echo "iOS Simulator UDID:   ${IOS_UDID:-<none found>}"
echo "watch Simulator UDID: ${WATCH_UDID:-<none found>}"

# --- 3. Build the iOS app (embeds widgets + watch app + watch widgets) -------
hdr "3/5  xcodebuild — ChronosFlow (iOS app, embeds all extensions)"
if [[ -n "$IOS_UDID" ]]; then
  if xcodebuild -project "$PROJECT" -scheme "$APP_SCHEME" -configuration Debug \
       -destination "id=$IOS_UDID" -derivedDataPath "$DD" \
       CODE_SIGNING_ALLOWED=NO build | $PRETTY; then
    record "Build $APP_SCHEME (iOS)" PASS
  else
    record "Build $APP_SCHEME (iOS)" FAIL
  fi
else
  red "No iOS Simulator available — cannot build the app."; record "Build $APP_SCHEME (iOS)" FAIL
fi

# --- 4. Build the watchOS app explicitly (separate platform) ----------------
hdr "4/5  xcodebuild — ChronosWatch (watchOS app)"
if [[ -n "$WATCH_UDID" ]]; then
  if xcodebuild -project "$PROJECT" -scheme "$WATCH_SCHEME" -configuration Debug \
       -destination "id=$WATCH_UDID" -derivedDataPath "$DD" \
       CODE_SIGNING_ALLOWED=NO build | $PRETTY; then
    record "Build $WATCH_SCHEME (watchOS)" PASS
  else
    record "Build $WATCH_SCHEME (watchOS)" FAIL
  fi
else
  red "No watchOS Simulator available — skipping explicit watch build."
  record "Build $WATCH_SCHEME (watchOS)" SKIP
fi

# --- 5. Simulator smoke launch (on-device verification) ---------------------
if [[ "$SMOKE" -eq 1 && -n "$IOS_UDID" ]]; then
  hdr "5/5  Smoke — boot Simulator, install, launch, screenshot"
  APP_PATH=$(find "$DD/Build/Products/Debug-iphonesimulator" -maxdepth 1 -name "$APP_SCHEME.app" 2>/dev/null | head -1)
  if [[ -n "$APP_PATH" ]]; then
    xcrun simctl boot "$IOS_UDID" 2>/dev/null || true
    xcrun simctl bootstatus "$IOS_UDID" -b || true
    if xcrun simctl install "$IOS_UDID" "$APP_PATH" \
       && xcrun simctl launch "$IOS_UDID" "$APP_BUNDLE_ID"; then
      sleep 4
      xcrun simctl io "$IOS_UDID" screenshot "build/chronos-launch.png" \
        && grn "Saved screenshot: ios/build/chronos-launch.png"
      record "Simulator smoke launch" PASS
    else
      record "Simulator smoke launch" FAIL
    fi
  else
    red "Built app not found under $DD — skipping smoke launch."
    record "Simulator smoke launch" FAIL
  fi
else
  hdr "5/5  Smoke launch skipped"
  record "Simulator smoke launch" SKIP
fi

# --- summary ----------------------------------------------------------------
hdr "Summary"
fail=0
for i in "${!STEPS[@]}"; do
  r="${RESULTS[$i]}"
  case "$r" in
    PASS) grn  "  PASS  ${STEPS[$i]}" ;;
    SKIP) printf "  SKIP  %s\n" "${STEPS[$i]}" ;;
    *)    red  "  FAIL  ${STEPS[$i]}"; fail=1 ;;
  esac
done
if [[ "$fail" -eq 0 ]]; then grn "All checks passed."; else red "One or more checks FAILED (see logs above)."; fi
exit "$fail"
