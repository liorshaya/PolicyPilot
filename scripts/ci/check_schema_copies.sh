#!/usr/bin/env bash
# Fixture copies match the backend resources, CI stage 1 (Document 6, Fixtures and Test Data, "Versioning"): every
# schema under fixtures/schemas/ and the demo fixtures the seed job loads have byte-identical copies under
# backend/src/main/resources/, so the fixtures and the running code cannot drift apart. The image is built from
# backend/, which is why the copies exist at all.
set -euo pipefail
cd "$(dirname "$0")/../.."

resources="backend/src/main/resources"
pairs=()
for fixture in fixtures/schemas/*.json; do
  pairs+=("$fixture:$resources/schemas/$(basename "$fixture")")
done
for name in policy.he.md ruleset.v1.json; do
  pairs+=("fixtures/policies/consumer-lending/$name:$resources/fixtures/consumer-lending/$name")
done

status=0
for pair in "${pairs[@]}"; do
  fixture="${pair%%:*}"
  copy="${pair#*:}"
  if [ ! -f "$copy" ]; then
    echo "MISSING: $copy (no backend copy of $fixture)"
    status=1
    continue
  fi
  if ! cmp -s "$fixture" "$copy"; then
    echo "DRIFT: $fixture differs from $copy"
    diff -u "$fixture" "$copy" | head -n 40 || true
    status=1
  fi
done

if [ "$status" -eq 0 ]; then
  echo "fixture copies OK (${#pairs[@]} file(s) identical)"
fi
exit "$status"
