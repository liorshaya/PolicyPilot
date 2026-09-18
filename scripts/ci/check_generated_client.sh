#!/usr/bin/env bash
# The generated TypeScript client is up to date, CI stage 1 (Document 6, Frontend Test Design: the client is
# generated from the OpenAPI document at build time). The client and its generate:api script arrive on day 6;
# until the generated folder exists this check reports SKIP and passes.
set -euo pipefail
cd "$(dirname "$0")/../.."

generated="frontend/src/api/generated"
if [ ! -d "$generated" ]; then
  echo "SKIP: $generated does not exist yet (the client is generated from the OpenAPI document on day 6)"
  exit 0
fi
if ! grep -q '"generate:api"' frontend/package.json; then
  echo "FAIL: $generated exists but frontend/package.json has no generate:api script"
  exit 1
fi
(cd frontend && npm run --silent generate:api)
if ! git diff --quiet -- "$generated"; then
  echo "FAIL: the generated client is stale; run 'npm run generate:api' in frontend/ and commit the result"
  git --no-pager diff --stat -- "$generated"
  exit 1
fi
echo "generated client OK"
