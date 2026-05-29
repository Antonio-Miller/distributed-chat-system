# Assignment 2 — Implementation Status (checklist)

Generated: 2026-05-29

## Summary

This document lists what is implemented in the `assign2` implementation and what is missing or should be mentioned in the final report.

## Implemented (Done)

- [x] TCP client-server architecture using Java SE 21
- [x] Virtual threads for per-connection handling
- [x] User registration and authentication with salted SHA-256 passwords persisted to `data/users.db`
- [x] Token-based session resumption (`RESUME <token>`) with token TTL (2 hours)
- [x] Multiple chat rooms with timeline-style messages and history on join
- [x] Ability to create normal rooms and AI rooms (`CREATEAI <room>|<prompt>`) from client
- [x] AI rooms integrated with local Ollama via HTTP (`AiService`) — server sends room prompt + timeline and posts Bot replies as `Bot:`
- [x] Shared data protected with explicit `ReentrantLock` and `Condition` (no `java.util.concurrent` collections)
- [x] Bounded per-session `OutboundQueue` with drop-oldest behavior to prevent slow-client blocking
- [x] Fault tolerance for broken TCP: client reconnect + `RESUME` binds new connection to existing session, preserving room membership and outbox
- [x] Minimal JSON helpers and HTTP client for Ollama integration (`JsonUtil`, `AiService`)
- [x] Command protocol implemented (LOGIN, REGISTER, RESUME, ROOMS, JOIN/CREATE, CREATEAI/JOINAI, MSG, LEAVE, QUIT)
- [x] Helper scripts and compile instructions included in README and `scripts/`

## Partially implemented / Notes (mention in report)

- [x] TLS/SSL channels: **NOW IMPLEMENTED** — Server and client support TLS 1.2+ with `--tls` flag. Requires self-signed keystore via `keytool` or `scripts/setup-tls.sh`.
- [x] Session persistence across server restarts: **NOW IMPLEMENTED** — Sessions and tokens persisted to `data/sessions.db`; server loads them on startup.
- [x] Basic integration tests: **NOW IMPLEMENTED** — `src/test/java/chat/test/ChatSystemTest.java` with manual test cases.
- [x] Packaged JAR artifact: **NOW IMPLEMENTED** — `build.sh` (Linux/macOS) and `build.bat` (Windows) scripts produce `ChatSystem-1.0.jar`.

## Files implementing core features (reference)

- Authentication and persistence: [src/main/java/chat/server/AuthService.java](src/main/java/chat/server/AuthService.java#L1)
- Token/session management: [src/main/java/chat/server/SessionManager.java](src/main/java/chat/server/SessionManager.java#L1) and [src/main/java/chat/server/Session.java](src/main/java/chat/server/Session.java#L1)
- Room handling and AI bot: [src/main/java/chat/server/Room.java](src/main/java/chat/server/Room.java#L1) and [src/main/java/chat/server/AiService.java](src/main/java/chat/server/AiService.java#L1)
- Outbound queue and slow-client handling: [src/main/java/chat/server/OutboundQueue.java](src/main/java/chat/server/OutboundQueue.java#L1)
- Connection handling (virtual threads, reconnect logic): [src/main/java/chat/server/ClientConnection.java](src/main/java/chat/server/ClientConnection.java#L1) and [src/main/java/chat/client/ChatClient.java](src/main/java/chat/client/ChatClient.java#L1)
- Protocol constants: [src/main/java/chat/common/Protocol.java](src/main/java/chat/common/Protocol.java#L1)
- README and run scripts: [README.md](README.md#L1), [scripts/start-server.sh](scripts/start-server.sh#L1), [scripts/start-client.sh](scripts/start-client.sh#L1)

## Recommendations for the final report

- Explicitly state the concurrency design choices: virtual threads, `ReentrantLock` usage, bounded `OutboundQueue` and rationale for dropping oldest messages.
- Document fault-tolerance behavior and limitations (in-memory sessions, token TTL, behavior on server restart).
- Mention optional Ollama dependency and how to configure environment variables (`OLLAMA_URL`, `OLLAMA_MODEL`) as in README.
- Note missing TLS support and lack of automated tests; suggest improvements and testing steps.

## Quick test steps (for the report)

```bash
cd assign2
mkdir -p out
find src/main/java -name "*.java" | sort > sources.txt
javac --release 21 -d out @sources.txt
java -cp out chat.server.ChatServer 12345
# in another terminal
java -cp out chat.client.ChatClient localhost 12345
```

---
If you want, I can: run a local compile/test, add a short section to the README summarizing these points, or produce a polished final-report.md for submission.
