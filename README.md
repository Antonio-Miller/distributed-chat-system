# Distributed Chat System

A multi-user **TCP chat system in Java 21**. Users authenticate, join rooms and exchange messages through a central server. The system recovers sessions after a broken connection, applies backpressure with bounded outbound queues, supports optional **TLS encryption**, and offers **AI-assisted rooms** that call a local LLM through Ollama.

Built by a team of 3 for the Parallel and Distributed Computing course (L.EIC, FEUP, 2025/26), Assignment 2. Grade: **17.6/20**.

## Highlights

- **Java 21 virtual threads**: one lightweight thread per client with a simple blocking I/O model
- **Explicit concurrency control**: shared state protected with locks and conditions
- **Fault tolerance**: sessions persist on disk and resume after a disconnection
- **Security**: salted SHA-256 password hashing and optional TLS
- **AI rooms**: room messages can be answered by a local model through Ollama

## Team

António Miller, Francisco Bandeira, José Reis

---

# CPD Assignment 2

## TCP Chat System in Java 21

This report documents the design, implementation, setup, installation, and execution of the Assignment 2 project. The project is a multi-user TCP chat system implemented in Java 21. It explores client-server communication, concurrency, synchronization, persistence, fault tolerance, security, and optional AI integration through a local Ollama model.

Group members:

1. Antonio Miller (up202008423)
2. Francisco Bandeira (up202208526)
3. Jose Reis (up202307304)

## Abstract

The developed system implements a terminal-based chat platform where multiple users can authenticate, join rooms, exchange messages, recover from broken TCP connections, and optionally interact with AI-assisted rooms. The application follows a classic client-server architecture: a central server accepts TCP connections, manages shared state, and coordinates message delivery, while clients provide a command-line interface for user interaction. The implementation uses Java 21 virtual threads to support many simultaneous clients with a simple blocking I/O model. Shared mutable state is protected with explicit locks and conditions, and persistent files are used for user credentials and resumable sessions.

## 1. Introduction

Distributed chat systems are useful case studies because they combine networking, concurrency, state management, synchronization, protocol design, and failure handling. This project implements those concepts without relying on a large external framework. Instead, it uses Java SE APIs, line-based TCP communication, explicit concurrency control, and small service classes with clear responsibilities.

The main objective is to provide a functional chat server where authenticated users can communicate in rooms. Beyond basic messaging, the project includes session resumption after disconnection, bounded outbound queues for backpressure, optional TLS encryption, and AI rooms that call a local Ollama endpoint.

## 2. System Overview

The system is divided into three main packages:

```text
src/main/java/chat/
  common/
    Protocol.java
  client/
    ChatClient.java
  server/
    ChatServer.java
    ClientConnection.java
    ServerState.java
    AuthService.java
    SessionManager.java
    Session.java
    SessionPersistence.java
    OutboundQueue.java
    RoomManager.java
    Room.java
    RoomInfo.java
    AiService.java
    JsonUtil.java
    SslUtil.java
    PasswordUtil.java
    UserRecord.java
```

`ChatServer` is the server entry point. It creates the shared server state, opens a TCP or TLS server socket, accepts incoming connections, and starts one virtual thread per client connection.

`ChatClient` is the terminal client. It connects to the server, translates local slash commands into protocol messages, receives server events, and automatically attempts to reconnect using a session token if the connection is lost.

`Protocol` centralizes the server-to-client response labels, such as `OK`, `ERR`, `TOKEN`, `ROOM`, `CHAT`, `JOINED`, and `HISTORY_BEGIN`.

## 3. Concepts Explored and Implementation

### 3.1 Client-Server Architecture

The project uses a centralized server model. Clients do not communicate directly with each other; instead, all messages pass through the server. This simplifies coordination because authentication, room membership, message history, and broadcasting are all managed in one place.

The server listens on a configurable port, with `12345` as the default. For every accepted socket, `ChatServer` creates a `ClientConnection`. This object is responsible for reading commands from that client, validating whether the user is authenticated, and dispatching commands to the appropriate service.

The communication protocol is line-based and encoded with UTF-8. Each client command is sent as a single text line, for example:

```text
LOGIN alice 1234
JOIN General
MSG hello everyone
```

The server also responds with text lines:

```text
OK Connected. Authenticate with LOGIN, REGISTER, or RESUME.
TOKEN <session-token>
CHAT [12:30:00] alice: hello everyone
```

This design makes the protocol easy to test manually with terminal clients and easy to inspect during debugging.

### 3.2 Concurrency with Java 21 Virtual Threads

The project uses Java 21 virtual threads through `Thread.startVirtualThread(...)`. Virtual threads are used for:

- Each accepted client connection.
- Each per-connection writer loop.
- Automatic client reconnection attempts.
- AI room responses, so that a slow Ollama request does not block the room or the main server loop.

This approach keeps the code simple because it can use blocking socket I/O while still scaling better than a traditional one-platform-thread-per-client design.

### 3.3 Synchronization and Shared State

The assignment explores explicit synchronization over ordinary Java collections. The implementation uses `ReentrantLock` and `Condition` instead of concurrent collection classes.

The main synchronized components are:

- `AuthService`: stores registered users in a `HashMap` protected by a lock.
- `SessionManager`: stores sessions by username and token using locked `HashMap` instances.
- `RoomManager`: stores the available rooms in a locked `HashMap`.
- `Room`: stores room members and the message timeline using a locked `HashSet` and `ArrayList`.
- `OutboundQueue`: implements a bounded queue with `ArrayDeque`, `ReentrantLock`, and `Condition`.

The implementation follows a snapshot pattern in the room code. For example, when a message is posted, the room records the message while holding the lock, copies the current recipients, releases the lock, and then enqueues the message to each recipient. This reduces the time spent inside critical sections and avoids writing to sockets while holding room locks.

### 3.4 Authentication and Password Storage

Authentication is implemented in `AuthService`, `PasswordUtil`, and `UserRecord`.

Users can authenticate with:

```text
LOGIN <username> <password>
```

or create a new account with:

```text
REGISTER <username> <password>
```

Usernames must match the validation rule `[A-Za-z0-9_.-]{3,32}`. Passwords are not stored directly. During registration, the server creates a random salt using `SecureRandom`, hashes the password using SHA-256 over `salt + password`, and persists the result in `data/users.db` using the format:

```text
username:saltHex:sha256(salt+password)
```

The project also creates default users on first run:

```text
alice/1234
bob/1234
eve/1234
```

When comparing password hashes, `UserRecord` uses a constant-time comparison function to reduce timing-based leakage.

### 3.5 Session Resumption and Fault Tolerance

After a successful login or registration, the server sends a session token:

```text
TOKEN <token>
```

The client stores this token in memory. If the TCP connection breaks, the client attempts to reconnect and sends:

```text
RESUME <token>
```

`SessionManager` validates the token and reattaches the new connection to the existing `Session`. This preserves the authenticated identity, the current room, and pending outbound messages. Tokens have a two-hour time-to-live and are persisted to `data/sessions.db`, which allows session recovery even after a server restart, as long as the token has not expired.

This design demonstrates fault tolerance at the application level. TCP itself does not preserve application sessions across disconnections, so the project adds explicit token-based recovery.

### 3.6 Rooms, Broadcasts, and Message History

Rooms are managed by `RoomManager` and represented by `Room`.

The server starts with two normal rooms:

```text
General
Library
```

Users can list rooms, join existing rooms, or create new normal rooms:

```text
ROOMS
JOIN <room name>
CREATE <room name>
```

When a user joins a room, the server sends the room history between `HISTORY_BEGIN` and `HISTORY_END` markers. New messages are timestamped and stored in the room timeline. Join and leave events are also represented as system messages, for example:

```text
[12:30:00] [alice enters the room]
```

Broadcasting is implemented by enqueueing messages into each member's `OutboundQueue`. The room itself does not write directly to sockets.

### 3.7 Backpressure and Slow Client Handling

Each authenticated session owns a bounded `OutboundQueue` with a default capacity of 512 messages. The queue uses an `ArrayDeque`, a `ReentrantLock`, and a `Condition` to coordinate producers and the per-connection writer thread.

If a client receives messages too slowly and the queue reaches capacity, the oldest pending messages for that client are dropped and an error message is enqueued. This prevents one slow client from blocking broadcasts to other clients.

### 3.8 AI Room Integration

The project includes AI-assisted rooms through `AiService`. Users can create an AI room with:

```text
CREATEAI <room name>|<prompt>
```

When a user sends a message inside an AI room, the room captures the timeline and starts a virtual thread that calls the local Ollama HTTP API. The prompt sent to Ollama combines:

- A fixed instruction describing the bot as a chat-room participant.
- The room-specific prompt.
- The current conversation timeline.

By default, the service uses:

```text
OLLAMA_URL=http://localhost:11434/api/generate
OLLAMA_MODEL=llama3.2
```

These values can be overridden with environment variables or Java system properties. The implementation also supports a mock mode through `MOCK_AI`, which is useful when Ollama is unavailable during testing.

### 3.9 Optional TLS Support

The server can run in TLS mode when started with the `--tls` flag. TLS support is implemented in `SslUtil`, which creates Java SSL socket factories from PKCS12 keystores or truststores.

Server command with TLS:

```bash
java -cp out chat.server.ChatServer 12345 data/users.db --tls
```

Client command with TLS:

```bash
java -cp out chat.client.ChatClient localhost 12345 --tls
```

If the server cannot initialize TLS because the keystore is missing or invalid, it prints a warning and falls back to plain TCP. The client also attempts plain TCP if its TLS connection fails.

### 3.10 Persistence

The project persists two types of state:

- `data/users.db`: registered users, salts, and password hashes.
- `data/sessions.db`: active session tokens and expiration times.

Room histories are kept in memory and are not persisted across server restarts. This is a reasonable design choice for the assignment because persistence is focused on authentication and session recovery.

## 4. Setup and Installation

### 4.1 Requirements

Install the following software:

- Java SE 21 or newer.
- A terminal or shell compatible with the commands below.
- Optional: Ollama, if AI rooms will be used.
- Optional: `keytool`, included with the JDK, if TLS mode will be used.

Check the Java version:

```bash
java -version
javac -version
```

Both commands should report version 21 or newer.

### 4.2 Project Directory

From the repository root, enter the assignment directory:

```bash
cd assign2
```

### 4.3 Compile on Windows

Using PowerShell:

```powershell
.\scripts\compile.ps1
```

This compiles the Java source files into:

```text
out/
```

Alternatively, build the runnable JAR:

```powershell
.\build.bat
```

This produces:

```text
ChatSystem-1.0.jar
```

### 4.4 Compile on Linux, macOS, or Git Bash

Use the build script:

```bash
./build.sh
```

or compile manually:

```bash
mkdir -p out
find src/main/java -name "*.java" | sort > sources.txt
javac --release 21 -d out @sources.txt
```

The build script also creates:

```text
ChatSystem-1.0.jar
```

## 5. How to Run

### 5.1 Run the Server from Compiled Classes

```bash
java -cp out chat.server.ChatServer [port] [users-file] [--tls]
```

Examples:

```bash
java -cp out chat.server.ChatServer
java -cp out chat.server.ChatServer 12345
java -cp out chat.server.ChatServer 9000 data/users.db
java -cp out chat.server.ChatServer 12345 data/users.db --tls
```

If omitted, the default port is `12345` and the default users file is `data/users.db`.

### 5.2 Run the Client from Compiled Classes

Open a second terminal and run:

```bash
java -cp out chat.client.ChatClient [host] [port] [--tls]
```

Examples:

```bash
java -cp out chat.client.ChatClient
java -cp out chat.client.ChatClient localhost 12345
java -cp out chat.client.ChatClient localhost 12345 --tls
```

The default host is `localhost` and the default port is `12345`.

### 5.3 Run Using the JAR

After running `build.bat` or `build.sh`, start the server with:

```bash
java -jar ChatSystem-1.0.jar [port] [users-file] [--tls]
```

Start a client with:

```bash
java -cp ChatSystem-1.0.jar chat.client.ChatClient [host] [port] [--tls]
```

### 5.4 Run Using Helper Scripts

PowerShell:

```powershell
.\scripts\start-server.ps1 [port]
.\scripts\start-client.ps1 [host] [port]
```

Linux, macOS, or Git Bash:

```bash
./scripts/start-server.sh [port]
./scripts/start-client.sh [host] [port]
```

These scripts compile the project into `out/` and then start the requested process.

## 6. Client Commands

Before authentication:

```text
/login <username> <password>
/register <username> <password>
```

After authentication:

```text
/rooms                          List available rooms
/ollama                         Check whether the server can reach Ollama
/join <room name>               Join an existing room or create a normal room
/create <room name>             Same behavior as /join for normal rooms
/createai <room>|<prompt>       Create and join an AI room
/joinai <room>|<prompt>         Protocol alias for creating and joining an AI room
/leave                          Leave the current room
/quit                           Disconnect from the server
/help                           Show local client help
```

Any line that does not start with `/` is sent as a chat message after the user has joined a room.

Example session:

```text
/login alice 1234
/join General
hello everyone
/createai AI-room|Summarize availability proposals and suggest a meeting time.
I can meet Monday at 10 or Tuesday after 14.
```

## 7. Ollama Setup for AI Rooms

Install Ollama from:

```text
https://ollama.com/download
```

Start the Ollama service:

```bash
ollama serve
```

In another terminal, pull the default model:

```bash
ollama pull llama3.2
```

Then start the chat server normally. From a connected client, check AI availability with:

```text
/ollama
```

To change the model or endpoint, use environment variables:

```bash
OLLAMA_MODEL=llama3.2:1b java -cp out chat.server.ChatServer
OLLAMA_URL=http://localhost:11434/api/generate java -cp out chat.server.ChatServer
```

or Java system properties:

```bash
java -Dollama.model=llama3.2:1b -cp out chat.server.ChatServer
java -Dollama.url=http://localhost:11434/api/generate -cp out chat.server.ChatServer
```

For testing without a real LLM:

```bash
MOCK_AI=1 java -cp out chat.server.ChatServer
```

## 8. Optional TLS Setup

Generate a server keystore:

```bash
keytool -genkeypair \
  -alias chat-server \
  -keystore data/chat-server.p12 \
  -storetype PKCS12 \
  -storepass 12345678 \
  -keypass 12345678 \
  -validity 365 \
  -keyalg RSA \
  -keysize 2048 \
  -dname "CN=localhost,O=CPD,C=PT" \
  -noprompt
```

Export the server certificate:

```bash
keytool -exportcert \
  -alias chat-server \
  -keystore data/chat-server.p12 \
  -storepass 12345678 \
  -rfc \
  -file data/chat-server.crt
```

Import the certificate into the client truststore:

```bash
keytool -importcert \
  -alias chat-server \
  -file data/chat-server.crt \
  -keystore data/chat-client.p12 \
  -storetype PKCS12 \
  -storepass 12345678 \
  -noprompt
```

Run with TLS:

```bash
java -cp out chat.server.ChatServer 12345 data/users.db --tls
java -cp out chat.client.ChatClient localhost 12345 --tls
```

## 9. Testing

The project includes a basic integration test in:

```text
src/test/java/chat/test/ChatSystemTest.java
```

First compile the main project:

```bash
cd assign2
./build.sh
```

Start the server on port `12345` in one terminal:

```bash
java -cp out chat.server.ChatServer 12345
```

Compile the test in another terminal:

```bash
mkdir -p out-test
javac --release 21 -cp out -d out-test src/test/java/chat/test/ChatSystemTest.java
```

Run the test on Linux, macOS, or Git Bash:

```bash
java -ea -cp out:out-test chat.test.ChatSystemTest
```

Run the test on Windows PowerShell:

```powershell
java -ea -cp "out;out-test" chat.test.ChatSystemTest
```

The test validates connection handling, user registration, token generation, room listing, joining, message sending, and session resumption.

## 10. Discussion

The implementation demonstrates several important distributed systems ideas. The first is separation between transport-level connections and application-level sessions. A TCP socket can fail at any time, but the user session can survive if the application gives it a persistent identity through tokens.

The second important idea is safe shared-state coordination. Multiple users may join rooms, send messages, register accounts, or resume sessions at the same time. The project protects this state with explicit locks and avoids holding locks while performing slow operations such as socket writes or HTTP requests.

The third idea is backpressure. A chat server should not allow a slow client to block the entire system. The bounded outbound queue makes message delivery independent per client, limiting the damage caused by one slow receiver.

Finally, the AI room feature shows how an external service can be integrated into a distributed application. The room remains responsive because the Ollama request is executed in a separate virtual thread, and failures are converted into chat-visible bot messages instead of crashing the server.

## 11. Limitations and Future Work

The current system stores room histories only in memory, so they disappear after a server restart. A future version could persist room timelines to disk or a database. The protocol is intentionally simple and text-based; a production system could use structured messages such as JSON with stricter parsing and versioning. TLS is optional and uses local keystore files, which is acceptable for an academic project but would need stronger certificate management in production.

Additional improvements could include private messages, user presence lists, room deletion, administrative commands, rate limiting, and a more complete automated test suite covering TLS, AI mock mode, invalid commands, and concurrent clients.

## 12. Conclusion

This assignment implements a complete Java 21 TCP chat system that combines networking, concurrency, synchronization, persistence, security, and optional AI integration. The project demonstrates how distributed systems concepts can be implemented directly with Java SE APIs while maintaining a clear architecture. The use of virtual threads simplifies the concurrent design, explicit locks make synchronization visible, and token-based session resumption provides practical fault tolerance for broken TCP connections.
