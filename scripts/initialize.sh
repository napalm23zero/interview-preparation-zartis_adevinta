#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# Keep local secrets and port choices untracked; the example is only a first-run template.
if [ ! -f "$root/.env-dev" ]; then
  cp -n "$root/.env-dev.example" "$root/.env-dev"
  printf '%s\n' 'Created .env-dev from .env-dev.example.'
fi
