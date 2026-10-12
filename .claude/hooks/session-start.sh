#!/bin/bash
# Prepares a Claude Code cloud session so the full check runs:
#   ./gradlew :core:test lint testDebugUnitTest assembleDebug
# Installs the Android SDK, writes local.properties and warms the Gradle cache.
# Idempotent: every step is skipped when its result is already present.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK_ROOT=/opt/android-sdk
CLT_ZIP=commandlinetools-linux-16111833_latest.zip
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# 1. Android command-line tools
if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools..."
  tmp=$(mktemp -d)
  curl -sSLo "$tmp/clt.zip" "https://dl.google.com/android/repository/$CLT_ZIP"
  unzip -q "$tmp/clt.zip" -d "$tmp"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 2. SDK packages (sdkmanager skips packages that are already installed)
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
if [ ! -d "$SDK_ROOT/platforms/android-37.0" ] || [ ! -d "$SDK_ROOT/build-tools/37.0.0" ] || [ ! -d "$SDK_ROOT/platform-tools" ]; then
  echo "Installing Android SDK packages..."
  "$SDKMANAGER" "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" > /dev/null
fi

# 3. Point Gradle and tools at the SDK
echo "sdk.dir=$SDK_ROOT" > "$PROJECT_DIR/local.properties"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$SDK_ROOT" >> "$CLAUDE_ENV_FILE"
fi

# 4. Warm the Gradle cache. Maven Central sometimes answers 429 Too Many Requests;
#    each attempt caches more, so retry a few times with limited parallelism.
cd "$PROJECT_DIR"
for attempt in 1 2 3 4 5; do
  if ./gradlew :core:test assembleDebug --max-workers=2 --console=plain -q > /dev/null 2>&1; then
    echo "Gradle cache warmed (attempt $attempt)."
    exit 0
  fi
  echo "Gradle warm-up attempt $attempt failed, retrying..."
  sleep $((attempt * 10))
done
echo "WARNING: Gradle warm-up did not complete; the first build in this session may need retries." >&2
exit 0
