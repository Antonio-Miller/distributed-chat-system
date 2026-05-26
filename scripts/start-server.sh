#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PORT=${1:-12345}

mkdir -p "$ROOT/out"
find "$ROOT/src/main/java" -name "*.java" | sort > "$ROOT/sources.txt"
javac --release 21 -d "$ROOT/out" @"$ROOT/sources.txt"
exec java -cp "$ROOT/out" chat.server.ChatServer "$PORT"
