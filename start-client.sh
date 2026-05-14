#!/bin/sh
set -eu

exec "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/scripts/start-client.sh" "$@"