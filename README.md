# CPD 2025/2026 - TCP Chat System in Java 21

Multi-user client-server chat system for the CPD Distributed Systems assignment.

## Features

- TCP client-server architecture with Java SE 21 virtual threads.
- User authentication (LOGIN/REGISTER) with salted SHA-256 password hashing persisted to file.
- Token-based session resumption after broken TCP connections (2-hour TTL).
- Multiple chat rooms with timeline-style messages and history on join.
- AI rooms with a configurable prompt backed by a local LLM (Ollama).
- Shared data structures protected with `ReentrantLock` / `Condition` (no `java.util.concurrent` collections).
- Bounded per-session outbound queues prevent slow clients from blocking broadcasts.

## Requirements

- Java SE 21 or newer.
- (Optional) Ollama running locally for AI rooms.

## Compile

Linux / macOS / Git Bash:

```bash
cd assign2
./build.sh
```

Or manually:

```bash
mkdir -p out
find src/main/java -name "*.java" | sort > sources.txt
javac --release 21 -d out @sources.txt
```

PowerShell:

```powershell
cd assign2
.\scripts\compile.ps1
# or use the build script:
.\build.bat
```

## Run server

```bash
java -cp out chat.server.ChatServer [port]
```

Default port is `12345`. The server creates `data/users.db` automatically.

Default users: `alice/1234`, `bob/1234`, `eve/1234`.

Or use the helper scripts:

```bash
./scripts/start-server.sh [port]
# or on Windows:
.\scripts\start-server.ps1 [port]
```

## Run clients

Open two or more terminals:

```bash
java -cp out chat.client.ChatClient [host] [port]
```

Or:

```bash
./scripts/start-client.sh [host] [port]
# or on Windows:
.\scripts\start-client.ps1 [host] [port]
```

## Client commands

Before authentication:

```
/login <username> <password>
/register <username> <password>
```

After authentication:

```
/rooms                          List available rooms
/join <room name>               Join or create a normal room
/create <room name>             Same as /join
/createai <room>|<prompt>       Create an AI room with a system prompt
/leave                          Leave the current room
/quit                           Disconnect
```

Any line that does not start with `/` is sent as a chat message.

## Example session

```
/login alice 1234
/join General
hello everyone
/createai AI doodle|Summarize availability proposals and suggest a meeting time.
I can meet Monday at 10 or Tuesday after 14.
```

## Security & TLS (optional)

The server and client support optional TLS/SSL encryption (TLS 1.2+):

### Setup TLS (one-time)

Generate a self-signed keystore:

```bash
# Option 1: Use the helper script
cd assign2
./scripts/setup-tls.sh

# Option 2: Use keytool manually
keytool -genkey -alias chat-server \
  -keystore data/chat-server.p12 \
  -storetype PKCS12 \
  -storepass 12345678 \
  -validity 365 \
  -keyalg RSA -keysize 2048 \
  -dname "CN=localhost,O=CPD,C=PT" \
  -noprompt
```

### Run with TLS

```bash
# Start server with TLS
java -cp out chat.server.ChatServer 12345 data/users.db --tls

# Connect with TLS
java -cp out chat.client.ChatClient localhost 12345 --tls
```

If the keystore is not found, the server falls back to plain TCP (with a warning).

## Ollama setup (for AI rooms)

Default settings:

```
OLLAMA_URL=http://localhost:11434/api/generate
OLLAMA_MODEL=llama3.2
```

Override with environment variables or system properties:

```bash
java -Dollama.model=llama3.2:1b -cp out chat.server.ChatServer 12345
```

## Protocol

Client to server:

```
LOGIN <username> <password>
REGISTER <username> <password>
RESUME <token>
ROOMS
JOIN <room name>
CREATE <room name>
CREATEAI <room name>|<prompt>
JOINAI <room name>|<prompt>
MSG <text>
LEAVE
QUIT
```

Server to client:

```
OK <text>
ERR <text>
TOKEN <token>
ROOM <NORMAL|AI> <room name>
END_ROOMS
JOINED <room name>
LEFT <room name>
HISTORY_BEGIN <room name>
CHAT <timeline line>
HISTORY_END <room name>
```

## Architecture

```
src/main/java/chat/
  common/
    Protocol.java              Protocol constants
  server/
    ChatServer.java            TCP accept loop with virtual threads
    ClientConnection.java      Per-connection command handler
    ServerState.java           Shared services container
    AuthService.java           Users, password hashing, persistence
    SessionManager.java        Token/session registry with persistence
    Session.java               Authenticated user state + output queue
    SessionPersistence.java    Load/save sessions to data/sessions.db
    OutboundQueue.java         Bounded lock/condition queue per session
    RoomManager.java           Room registry
    Room.java                  Timeline, members, broadcast, AI trigger
    RoomInfo.java              Room metadata record
    AiService.java             Ollama HTTP client
    JsonUtil.java              Minimal JSON helpers for Ollama
    SslUtil.java               TLS socket factory helpers
    PasswordUtil.java          SHA-256 hashing utilities
    UserRecord.java            User credential record
  client/
    ChatClient.java            Terminal client with reconnect logic + TLS
  
  test/java/chat/test/
    ChatSystemTest.java        Integration test suite
```

## Concurrency design

Room broadcasts never write directly to sockets. Each `Session` has a bounded `OutboundQueue` (`ArrayDeque` + `ReentrantLock` + `Condition`). A dedicated virtual-thread writer per connection drains the queue to the socket. If a client is too slow, the oldest queued messages are dropped for that client only.

All shared structures use ordinary Java collections with explicit locks:

- `AuthService`: `HashMap` + `ReentrantLock`
- `SessionManager`: `HashMap` + `ReentrantLock`
- `RoomManager`: `HashMap` + `ReentrantLock`
- `Room`: `ArrayList` + `HashSet` + `ReentrantLock`
- `OutboundQueue`: `ArrayDeque` + `ReentrantLock` + `Condition`

## Fault tolerance

After login/register the server sends a `TOKEN`. The client stores it in memory (never credentials). If the TCP connection breaks, the client reconnects and sends `RESUME <token>`. The server binds the new connection to the existing session, preserving room membership and queued messages. Tokens expire after 2 hours.

Sessions and tokens are persisted to `data/sessions.db` and reloaded on server restart, allowing users to resume even after server downtime.

## Testing

Run basic integration tests (server must be running on port 12345):

```bash
cd assign2

# Terminal 1: Start server
java -cp out chat.server.ChatServer 12345

# Terminal 2: Run tests
mkdir -p out-test
javac --release 21 -cp out -d out-test src/test/java/chat/test/ChatSystemTest.java
java -cp out:out-test chat.test.ChatSystemTest
```

The test suite validates:
- TCP connection handling
- User registration and authentication
- Token generation
- Room listing and joining
- Session resumption (fault tolerance)

## Data files

- `data/users.db` — User credentials (salted SHA-256 hashes), created/appended on first run with default users (alice/bob/eve).
- `data/sessions.db` — Active session tokens and expiration times, loaded on server startup to support session resumption across restarts.
- `data/chat-server.p12` — TLS keystore (for `--tls` mode), generated via `scripts/setup-tls.sh`.
