#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PORT=${1:-5000}

if ss -ltn "sport = :$PORT" | grep -q LISTEN; then
	echo "Port $PORT is already in use. Stop the process using it or choose another port." >&2
	exit 1
fi

mkdir -p "$ROOT/out"
find "$ROOT/src/main/java" -name "*.java" > "$ROOT/sources.txt"
javac -d "$ROOT/out" @"$ROOT/sources.txt"
exec java -cp "$ROOT/out" chat.server.Server "$PORT"
