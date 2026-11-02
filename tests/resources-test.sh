#!/bin/bash
set -eu
cd "$(dirname "$0")/.."

# Fake resource creation while retaining the real Compose parser, because YAML gets enough dark magic already.
docker() {
  case "${1-}:${2-}" in
    info:) [ "${MOCK_UNAVAILABLE:-0}" = 0 ]; return ;;
    network:inspect|volume:inspect) [ "${MOCK_EXISTING:-0}" = 1 ]; return ;;
    network:create|volume:create) printf 'created %s\n' "$*" >&2; return ;;
    compose:*) command docker "$@"; return ;;
  esac
  return 2
}
export -f docker

output=$(MOCK_EXISTING=0 bash scripts/resources.sh 2>&1)
[ "$(printf '%s\n' "$output" | grep -c '^created network create zartis$')" = 1 ]
[ "$(printf '%s\n' "$output" | grep -c '^created volume create zartis_')" = 15 ]
output=$(MOCK_EXISTING=1 bash scripts/resources.sh 2>&1)
[ -z "$output" ]
if output=$(MOCK_UNAVAILABLE=1 bash scripts/resources.sh 2>&1); then
  printf '%s\n' 'Expected unavailable Docker to fail.' >&2
  exit 1
fi
case "$output" in *'Docker is unavailable'*) ;; *) printf '%s\n' "$output" >&2; exit 1;; esac
printf '%s\n' 'Resource preparation checks passed.'
