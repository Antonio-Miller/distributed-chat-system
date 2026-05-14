#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HOST=${1:-localhost}
PORT=${2:-5000}

mkdir -p "$ROOT/out"
find "$ROOT/src/main/java" -name "*.java" > "$ROOT/sources.txt"
javac -d "$ROOT/out" @"$ROOT/sources.txt"
exec java -cp "$ROOT/out" chat.client.Client "$HOST" "$PORT"
