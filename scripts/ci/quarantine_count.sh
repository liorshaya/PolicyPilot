#!/usr/bin/env bash
# Prints the number of quarantined tests (Document 6, Flaky test policy) so the count cannot grow quietly.
# Backend: @Tag("quarantine"); frontend: test.skip / it.skip with a ticket comment.
set -euo pipefail
cd "$(dirname "$0")/../.."

backend=$( (grep -rl --include='*.java' '@Tag("quarantine")' backend/src/test 2>/dev/null || true) | wc -l | tr -d ' ')
frontend=$( (grep -rE --include='*.ts' --include='*.tsx' '\b(test|it|describe)\.skip\(' frontend/src frontend/e2e 2>/dev/null || true) | wc -l | tr -d ' ')
echo "### Quarantined tests"
echo ""
echo "| Backend classes tagged quarantine | Frontend skipped tests |"
echo "| --- | --- |"
echo "| $backend | $frontend |"
