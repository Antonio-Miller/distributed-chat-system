#!/usr/bin/env bash
set -euo pipefail

# run_all.sh — build, (optionally) setup TLS, start server and multiple clients in new terminals
# Usage: ./run_all.sh [--no-tls] [--tls] [--clients N] [--tests]

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# Defaults
USE_TLS="auto"   # auto | yes | no
CLIENTS=2
RUN_TESTS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-tls) USE_TLS=no; shift ;;
    --tls) USE_TLS=yes; shift ;;
    --clients) CLIENTS="$2"; shift 2 ;;
    --tests) RUN_TESTS=1; shift ;;
    -h|--help) echo "Usage: $0 [--no-tls|--tls] [--clients N] [--tests]"; exit 0 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# Detect terminal emulator and provide an open_term function
open_term() {
  local title="$1"; shift
  local cmd="$*"

  if command -v gnome-terminal >/dev/null 2>&1; then
    gnome-terminal --title="$title" -- bash -c "$cmd; echo; echo 'Press ENTER to close...'; read -r"
    return 0
  fi
  if command -v konsole >/dev/null 2>&1; then
    konsole --hold -p tabtitle="$title" -e bash -c "$cmd; echo; echo 'Press ENTER to close...'; read -r" &
    return 0
  fi
  if command -v xfce4-terminal >/dev/null 2>&1; then
    xfce4-terminal --title="$title" --hold -e "bash -c '$cmd; echo; echo \"Press ENTER to close...\"; read -r'" &
    return 0
  fi
  if command -v mate-terminal >/dev/null 2>&1; then
    mate-terminal --title="$title" -e "bash -c '$cmd; echo; echo \"Press ENTER to close...\"; read -r'" &
    return 0
  fi
  if command -v xterm >/dev/null 2>&1; then
    xterm -T "$title" -hold -e bash -c "$cmd" &
    return 0
  fi
  if command -v kitty >/dev/null 2>&1; then
    kitty --title "$title" bash -c "$cmd; echo; echo 'Press ENTER to close...'; read -r" &
    return 0
  fi

  # Fallback: run in background in current terminal
  echo "No GUI terminal found; running '$title' in background (stdout/stderr redirected)."
  bash -c "$cmd" > "$ROOT_DIR/$title.log" 2>&1 &
}

# Build JAR
echo "[run_all] Building project..."
chmod +x build.sh || true
./build.sh

JAR="ChatSystem-1.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Build failed: $JAR not found" >&2
  exit 1
fi

# TLS decision
KEYSTORE="data/chat-server.p12"
if [[ "$USE_TLS" == "auto" ]]; then
  if [[ -f "$KEYSTORE" ]]; then
    USE_TLS=yes
  else
    USE_TLS=no
  fi
fi

if [[ "$USE_TLS" == "yes" ]]; then
  if [[ ! -f "$KEYSTORE" ]]; then
    echo "Keystore not found. Generating self-signed keystore..."
    chmod +x scripts/setup-tls.sh || true
    ./scripts/setup-tls.sh
  fi
  SERVER_CMD="java -jar $JAR 12345 data/users.db --tls"
  CLIENT_CMD="java -cp $JAR chat.client.ChatClient localhost 12345 --tls"
else
  SERVER_CMD="java -jar $JAR 12345 data/users.db"
  CLIENT_CMD="java -cp $JAR chat.client.ChatClient localhost 12345"
fi

# Start server terminal
echo "[run_all] Starting server..."
open_term "Chat Server" "$SERVER_CMD"

# Give server some time to start
sleep 1.5

# Start client terminals
echo "[run_all] Starting $CLIENTS client(s)..."
for i in $(seq 1 "$CLIENTS"); do
  open_term "Chat Client $i" "$CLIENT_CMD"
  sleep 0.3
done

# Optionally run integration tests in a separate terminal
if [[ "$RUN_TESTS" -eq 1 ]]; then
  echo "[run_all] Starting integration tests (server must be running)..."
  TEST_CMD="mkdir -p out-test && javac --release 21 -cp out -d out-test src/test/java/chat/test/ChatSystemTest.java && java -cp out:out-test chat.test.ChatSystemTest; echo; echo 'Tests finished. Press ENTER to close...'; read -r"
  open_term "Integration Tests" "$TEST_CMD"
fi

echo "[run_all] Launched server and clients. Check the opened terminals."
echo "If a GUI terminal wasn't available, logs are in $ROOT_DIR/*.log"
