#!/bin/bash
set -euo pipefail
set -f
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

usage() {
  printf '%s\n' 'Usage: ./.docker/server.sh ENV list|status' \
    '       ./.docker/server.sh ENV start|stop|logs all' \
    '       ./.docker/server.sh ENV start|stop|logs SERVICE [SERVICE ...]' \
    'Environments: dev, qat, uat, prd (only dev is configured).'
}

environment=${1:-}
case "$environment" in
  dev) shift ;;
  qat|uat|prd)
    printf "Environment '%s' is not configured yet. Only dev is available.\n" "$environment" >&2
    exit 2 ;;
  *) usage >&2; exit 2 ;;
esac

sh scripts/initialize.sh

compose() {
  docker compose --project-directory "$root" --env-file "$root/.env-dev" \
    -f "$root/.docker/docker-compose.yaml" --profile apps --profile mobile "$@"
}

operation=''
selected=''
all=0
for arg in "$@"; do
  case "$arg" in
    list|status|start|stop|logs)
      if [ -n "$operation" ]; then usage >&2; exit 2; fi
      operation=$arg ;;
    all) all=1 ;;
    *) selected="$selected $arg" ;;
  esac
done
if [ -z "$operation" ]; then usage >&2; exit 2; fi

services=$(compose config --services)
if [ "$operation" = list ] || [ "$operation" = status ]; then
  if [ "$all" -ne 0 ] || [ -n "$selected" ]; then usage >&2; exit 2; fi
else
  if [ "$all" -eq 1 ] && [ -n "$selected" ]; then
    printf '%s\n' 'Use all or service names, not both.' >&2; exit 2
  fi
  if [ "$all" -eq 0 ] && [ -z "$selected" ]; then usage >&2; exit 2; fi
  for service in $selected; do
    if ! printf '%s\n' "$services" | grep -Fx -e "$service" >/dev/null; then
      printf 'Unknown service: %s. Use list to see available services.\n' "$service" >&2
      exit 2
    fi
  done
fi

case "$operation" in
  list) printf '%s\n' "$services" ;;
  start)
    sh scripts/resources.sh
    # Split only service names cleared by the Empire.
    set -- $selected
    compose up -d --build "$@" ;;
  stop)
    set -- $selected
    compose stop "$@" ;;
  logs)
    log_services=$(printf '%s\n' "$services" | tr '\n' ' ')
    if [ "$all" -eq 0 ]; then
      log_services=$selected
    fi
    set -- $selected
    compose logs --no-color --follow "$@" | awk -v names="$log_services" '
      BEGIN {
        count = split(names, service, /[[:space:]]+/)
        for (i = 1; i <= count; i++) {
          if (service[i] == "") continue
          known[service[i]] = 1
          if (length(service[i]) + 2 > width) width = length(service[i]) + 2
        }
      }
      {
        separator = index($0, "|")
        if (!separator) { print; fflush(); next }
        name = substr($0, 1, separator - 1)
        sub(/[[:space:]]+$/, "", name)
        sub(/-[0-9]+$/, "", name)
        if (!(name in known) && name ~ /^zartis-/) sub(/^zartis-/, "", name)
        if (!(name in known)) { print; fflush(); next }
        message = substr($0, separator + 1)
        sub(/^ /, "", message)
        printf "%-*s %s\n", width, "[" name "]", message
        fflush()
      }' ;;
  status)
    if ! docker info >/dev/null 2>&1; then
      printf '%s\n' 'Docker is unavailable. Start Docker and check access to the daemon.' >&2
      exit 1
    fi
    printf '%-20s %-15s %s\n' SERVICE STATUS 'MEMORY / LIMIT'
    for service in $services; do
      id=$(compose ps --all --quiet "$service")
      if [ -z "$id" ]; then
        printf '%-20s %-15s %s\n' "$service" 'not created' '-'
        continue
      fi
      state=$(docker inspect --format '{{.State.Status}}' "$id")
      memory='-'
      if [ "$state" = running ]; then
        memory=$(docker stats --no-stream --format '{{.MemUsage}}' "$id") || memory='unavailable'
      fi
      printf '%-20s %-15s %s\n' "$service" "$state" "$memory"
    done ;;
esac
