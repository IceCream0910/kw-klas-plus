#!/bin/sh
set -eu

if [ "${CONFIGURATION:-}" != Release ] || [ "${PLATFORM_NAME:-}" != iphoneos ]; then
    echo "Skipping Sentry symbols for non-device Release build."
    exit 0
fi

if [ "${SENTRY_AUTO_UPLOAD:-true}" = false ]; then
    echo "Skipping Sentry symbols: SENTRY_AUTO_UPLOAD=false."
    exit 0
fi

properties_file="${SRCROOT:?}/../sentry.properties"
if [ -f "$properties_file" ]; then
    export SENTRY_PROPERTIES="$properties_file"
fi

if [ -z "${SENTRY_AUTH_TOKEN:-}" ] && [ ! -f "$properties_file" ]; then
    echo "error: Set auth.token in the root sentry.properties or SENTRY_AUTH_TOKEN, or use SENTRY_AUTO_UPLOAD=false for validation builds." >&2
    exit 1
fi

export PATH="$PATH:/opt/homebrew/bin:/usr/local/bin"
if ! command -v sentry-cli >/dev/null 2>&1; then
    echo "error: Install sentry-cli with brew install getsentry/tools/sentry-cli." >&2
    exit 1
fi

if [ ! -d "${DWARF_DSYM_FOLDER_PATH:?}/${DWARF_DSYM_FILE_NAME:?}" ]; then
    echo "error: Missing app dSYM for this build." >&2
    exit 1
fi

export SENTRY_ORG=yun-taein
export SENTRY_PROJECT=kw-klas-plus-ios
exec sentry-cli debug-files upload "$DWARF_DSYM_FOLDER_PATH"
