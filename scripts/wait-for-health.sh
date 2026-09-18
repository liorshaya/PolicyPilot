#!/usr/bin/env bash
# Waits for the backend health check (Document 1, Definition of Done: the whole system is up in under 5 minutes).
# Usage: scripts/wait-for-health.sh [url] [seconds]
set -euo pipefail
url="${1:-http://localhost:8080/actuator/health}"
limit="${2:-300}"
start=$(date +%s)

until curl -fsS "$url" 2>/dev/null | grep -q '"status":"UP"'; do
  elapsed=$(( $(date +%s) - start ))
  if [ "$elapsed" -ge "$limit" ]; then
    echo "backend is not healthy after ${limit}s: $url"
    echo "look at the logs with: make logs"
    exit 1
  fi
  sleep 3
done
echo "backend healthy after $(( $(date +%s) - start ))s: $url"
