#!/usr/bin/env bash
# Schema copies match the backend resources, CI stage 1 (Document 6, Fixtures and Test Data, "Versioning"):
# every schema under fixtures/schemas/ has a byte-identical copy under backend/src/main/resources/schemas/,
# so the fixtures and the running code cannot drift apart.
set -euo pipefail
cd "$(dirname "$0")/../.."

status=0
count=0
for fixture in fixtures/schemas/*.json; do
  count=$((count + 1))
  name=$(basename "$fixture")
  copy="backend/src/main/resources/schemas/$name"
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
  echo "schema copies OK ($count file(s) identical)"
fi
exit "$status"
