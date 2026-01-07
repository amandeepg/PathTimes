#!/usr/bin/env bash
# Clears only the app cache for ca.amandeep.path, then force-stops and relaunches it.

set -euo pipefail

PKG="ca.amandeep.path"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-10}"

log() {
  # Consistent, easy-to-grep log prefix for troubleshooting.
  printf '[clear-cache-restart] %s\n' "$*"
}

log "Starting cache clear + restart for package: $PKG"

#adb wait-for-device
log "Checking if device is connected..."
adb get-state >/dev/null
log "Device reported via adb."

log "Determining cache clear strategy (pm supports --cache-only or run-as fallback)..."
if adb shell pm help | grep -q -- "--cache-only"; then
  if command -v timeout >/dev/null 2>&1; then
    log "Using (with timeout ${TIMEOUT_SECONDS}s): adb shell pm clear --user 0 --cache-only $PKG"
    if timeout "$TIMEOUT_SECONDS"s adb shell pm clear --user 0 --cache-only "$PKG"; then
      log "Cache-only clear completed."
    else
      status=$?
      log "Cache-only clear failed (exit $status); trying run-as cache wipe next."
      if adb shell run-as "$PKG" rm -rf "/data/data/$PKG/cache/*"; then
        log "run-as cache wipe succeeded."
      else
        log "run-as cache wipe failed; aborting without clearing app data."
        exit 1
      fi
    fi
  else
    log "timeout command unavailable; running cache-only clear without timeout."
    if adb shell pm clear --user 0 --cache-only "$PKG"; then
      log "Cache-only clear completed."
    else
      log "Cache-only clear failed; falling back to run-as cache wipe."
      if adb shell run-as "$PKG" rm -rf "/data/data/$PKG/cache/*"; then
        log "run-as cache wipe succeeded."
      else
        log "run-as cache wipe failed; aborting without clearing app data."
        exit 1
      fi
    fi
  fi
else
  log "pm clear --cache-only not supported; attempting run-as cache wipe..."
  # Fallback for older builds: remove cache via run-as (requires debuggable app or root)
  if adb shell run-as "$PKG" rm -rf "/data/data/$PKG/cache/*"; then
    log "run-as cache wipe succeeded."
  else
    log "run-as cache wipe failed; aborting without clearing app data."
    exit 1
  fi
fi

log "Force-stopping package..."
adb shell am force-stop "$PKG"

log "Resolving launcher activity..."
ACTIVITY=$(adb shell cmd package resolve-activity --brief "$PKG" 2>/dev/null | tail -n 1 | tr -d '\r')

if [[ "$ACTIVITY" == */* ]]; then
  log "Starting activity: $ACTIVITY"
  adb shell am start -n "$ACTIVITY"
else
  log "Activity not resolved; using monkey to launch."
  # If we cannot resolve the launcher activity, fall back to monkey to open the app.
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
fi

log "Cache cleared; app restarted."
