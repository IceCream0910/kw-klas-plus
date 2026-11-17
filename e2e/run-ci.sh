#!/usr/bin/env bash
set -euo pipefail

if [[ "${E2E_LIVE_MEDIA:-false}" == true ]]; then
    npm test --prefix e2e -- --target "$1"
else
    npm test --prefix e2e -- --target "$1" --exclude-tag live-media
fi
