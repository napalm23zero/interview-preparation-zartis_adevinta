#!/bin/sh
set -eu
set -f
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
sh scripts/initialize.sh

compose() {
  docker compose --project-directory "$root" --env-file "$root/.env-dev" \
    -f "$root/.docker/docker-compose.yaml" --profile apps --profile mobile "$@"
}

usage() {
  printf '%s\n' 'Usage: ./.docker/server.sh list|status' \
    '       ./.docker/server.sh start|stop all' \
    '       ./.docker/server.sh start|stop SERVICE [SERVICE ...]'
}

operation=''
selected=''
all=0
for arg in "$@"; do
  case "$arg" in
    list|status|start|stop)
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
    # Intentional word splitting: each name was checked against Compose services.
    set -- $selected
    compose up -d --build "$@" ;;
  stop)
    set -- $selected
    compose stop "$@" ;;
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
