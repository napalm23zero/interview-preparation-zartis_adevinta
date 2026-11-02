#!/bin/sh
set -eu
# Read the backend's YAML limit so the ingress does not strand oversized payloads at the gate.
config_file=${PHOTO_CONFIG_FILE:-/etc/loopmarket/backend-config.yml}
limit=$(sed -n 's/^photoMaxBytes:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$config_file")
case "$limit" in ''|*[!0-9]*) echo 'Invalid photoMaxBytes in backend config' >&2; exit 1;; esac
if [ "$limit" -lt 1 ] || [ "$limit" -gt 67108864 ]; then
  echo 'photoMaxBytes must be between 1 and 67108864' >&2
  exit 1
fi
# Allow base64 expansion and bounded JSON overhead; the API enforces the actual image limit.
NGINX_MAX_BODY_BYTES=$(( ((limit + 2) / 3) * 4 + 65536 ))
export NGINX_MAX_BODY_BYTES
exec /docker-entrypoint.sh "$@"
