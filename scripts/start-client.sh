#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HOST=${1:-localhost}
PORT=${2:-12345}

mkdir -p "$ROOT/out"
find "$ROOT/src/main/java" -name "*.java" | sort > "$ROOT/sources.txt"
javac --release 21 -d "$ROOT/out" @"$ROOT/sources.txt"
exec java -cp "$ROOT/out" chat.client.ChatClient "$HOST" "$PORT"
