#!/bin/sh
# Minimal Gradle bootstrap for DIVINE IT CONVERTOR.
# NOTE (Phase 2 honesty): this is NOT the official gradle-wrapper.jar, which
# is normally checked in from a Gradle distribution. This script reuses a
# cached Gradle 8.7 distribution, a `gradle` already on PATH, or downloads
# the 8.7 distribution zip from services.gradle.org (needs curl or wget and
# unzip). Android Studio can also sync this project without this script by
# selecting a local Gradle distribution in Settings > Build Tools > Gradle.
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
if [ -x "$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN=$(command -v gradle)
else
  echo "Downloading Gradle $GRADLE_VERSION..."
  mkdir -p "$DIST_DIR"
  TMP_ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o "$TMP_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "Need curl or wget to download Gradle, or install Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  unzip -q -o "$TMP_ZIP" -d "$DIST_DIR"
  GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"
fi
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
