#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker is unavailable. Start Docker and grant this user access before preparing the shared network and volumes.' >&2
  exit 1
fi

if ! docker network inspect zartis >/dev/null 2>&1; then
  # Compose expects this shared external network to exist before any service starts.
  docker network create zartis >/dev/null
fi

# Create only missing named volumes so repeated startup preserves databases and local model data.
volumes=$(docker compose --env-file "$root/.env-dev" --profile apps --profile mobile -f "$root/docker-compose.yaml" config --volumes)
for volume in $volumes; do
  if ! docker volume inspect "zartis_$volume" >/dev/null 2>&1; then
    docker volume create "zartis_$volume" >/dev/null
  fi
done
