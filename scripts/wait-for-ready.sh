#!/usr/bin/env sh
set -eu

base_url="${1:-http://localhost:8080}"
attempts="${READY_ATTEMPTS:-90}"

attempt=1
while [ "$attempt" -le "$attempts" ]; do
  if curl --fail --silent --show-error "$base_url/actuator/health/readiness" >/dev/null; then
    echo "Backend readiness is available at $base_url"
    exit 0
  fi
  sleep 1
  attempt=$((attempt + 1))
done

echo "Backend did not become ready after $attempts seconds: $base_url" >&2
exit 1
