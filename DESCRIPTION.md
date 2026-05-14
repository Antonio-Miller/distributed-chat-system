# CPD 2025/2026 — Distributed Chat System

> **Assignment deadline:** 29th May 2026 (grace period until 31st May 2026, 23:59)  
> **Language:** Java SE 21+  
> **Submission:** Gitlab@UP group repository

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Repository Structure](#3-repository-structure)
4. [Protocol Design](#4-protocol-design)
5. [Core Components](#5-core-components)
6. [Concurrency Model](#6-concurrency-model)
7. [Authentication & Tokens](#7-authentication--tokens)
8. [Room System](#8-room-system)
9. [AI Rooms & Ollama](#9-ai-rooms--ollama)
10. [Fault Tolerance & Reconnection](#10-fault-tolerance--reconnection)
11. [SSL / Secure Channels](#11-ssl--secure-channels)
12. [Setup & Installation](#12-setup--installation)
13. [Running the System](#13-running-the-system)
14. [Grading Criteria Summary](#14-grading-criteria-summary)

---

## 1. Project Overview

This project is a **multi-user, real-time TCP chat system** built in Java. Users connect to a central server, authenticate, and exchange text messages inside named chat rooms. The system supports:

- User registration and authentication with session tokens
- Multiple concurrent chat rooms
- Real-time message broadcasting (users see new messages instantly without typing)
- AI-powered rooms backed by a local LLM (Ollama)
- Automatic reconnection after TCP disconnections (without re-authentication)
- Optional SSL encrypted transport

### Example interaction

```
Room: Library
[Alice enters the room]
Alice: Hi people! Anyone?
[Eve enters the room]
Eve: I am giving free apples and Eve memecoin.
Alice: Give me an apple please.
```

---

## 2. System Architecture

```
┌─────────────┐        TCP / SSL        ┌──────────────────────────────────────┐
│  Client A   │ ◄──────────────────────► │                                      │
├─────────────┤                          │              SERVER                  │
│  Client B   │ ◄──────────────────────► │                                      │
├─────────────┤                          │  ┌──────────────┐  ┌──────────────┐  │
│  Client C   │ ◄──────────────────────► │  │ Auth Handler │  │Room Manager  │  │
└─────────────┘                          │  └──────┬───────┘  └──────┬───────┘  │
                                         │         │                  │          │
                                         │  ┌──────▼───────┐  ┌──────▼───────┐  │
                                         │  │Session Manager│  │Msg Broadcast │  │
                                         │  └──────────────┘  └──────────────┘  │
                                         │                                      │
                                         │  ┌──────────────┐                   │
                                         │  │  User Store  │  (file-based)     │
                                         │  └──────────────┘                   │
                                         └───────────────────┬──────────────────┘
                                                             │ HTTP (AI rooms only)
                                                   ┌─────────▼─────────┐
                                                   │   Ollama LLM       │
                                                   │  localhost:11434   │
                                                   └───────────────────┘
```

### Key design decisions

- One **virtual thread** per client connection (Java 21 `Thread.ofVirtual()`)
- Shared data structures protected with `java.util.concurrent.locks` (no concurrent collections)
- Non-blocking per-client output queues to prevent slow clients from stalling broadcasts
- Token-based sessions decoupled from TCP connections (reconnect without re-login)

---

## 3. Repository Structure

```
chat-system/
├── README.md                          # How to run (required for submission)
├── .gitignore
│
├── src/
│   └── main/
│       └── java/
│           └── chat/
│               ├── server/
│               │   ├── Server.java            # Entry point; accepts TCP connections
│               │   ├── ClientHandler.java     # One virtual thread per connected client
│               │   ├── SessionManager.java    # Token issuance, validation, expiry
│               │   ├── RoomManager.java       # Create, list, join rooms
│               │   ├── Room.java              # Room state + broadcast logic
│               │   └── AIRoom.java            # Extends Room; calls Ollama after each msg
│               │
│               ├── client/
│               │   ├── Client.java            # Entry point
│               │   ├── ConnectionManager.java # TCP connect + auto-reconnect loop
│               │   ├── MessageReceiver.java   # Background thread; prints incoming msgs
│               │   └── CLI.java               # User input loop (stdin)
│               │
│               ├── common/
│               │   ├── Message.java           # Shared message data structure
│               │   ├── Protocol.java          # Command constants and delimiters
│               │   └── Token.java             # Token value + expiry timestamp
│               │
│               └── auth/
│                   ├── AuthManager.java       # Credential validation logic
│                   └── UserStore.java         # File-based persistence (hashed passwords)
│
├── data/
│   └── users.txt                      # Hashed credential store (gitignored if sensitive)
│
├── certs/                             # SSL certificates (optional feature)
│   ├── server.keystore
│   └── client.truststore
│
└── scripts/
    ├── start-server.sh
    └── start-client.sh
```

### `.gitignore` essentials

```
data/users.txt
certs/
*.class
out/
```

---

## 4. Protocol Design

All messages between client and server are **plain text lines** (`\n` terminated) sent over the TCP socket. Define all command strings as constants in `Protocol.java`.

### Client → Server commands

| Command | Format | Description |
|---|---|---|
| `AUTH` | `AUTH <username> <password>` | First-time authentication |
| `TOKEN` | `TOKEN <token-value>` | Reconnect using existing token |
| `LIST` | `LIST` | Request list of available rooms |
| `JOIN` | `JOIN <room-name>` | Join or create a room |
| `MSG` | `MSG <text>` | Send a message to the current room |
| `LEAVE` | `LEAVE` | Leave the current room |
| `QUIT` | `QUIT` | Disconnect |

### Server → Client responses

| Response | Format | Description |
|---|---|---|
| `OK` | `OK <optional-data>` | Success |
| `TOKEN` | `TOKEN <token-value>` | Token issued after successful AUTH |
| `ROOMS` | `ROOMS <room1>,<room2>,...` | Room list response |
| `JOINED` | `JOINED <room-name>` | Confirmation of room entry |
| `CHAT` | `CHAT <username>: <text>` | A message from a room member |
| `EVENT` | `EVENT [<username> enters/leaves the room]` | Room presence event |
| `BOT` | `BOT <text>` | AI response in an AI room |
| `ERROR` | `ERROR <description>` | Something went wrong |

### Example session flow

```
Client:  AUTH alice secret123
Server:  OK
Server:  TOKEN eyJhbGc...abc123   (token stored by client)

Client:  LIST
Server:  ROOMS General,Library,AI-doodle

Client:  JOIN Library
Server:  JOINED Library
Server:  EVENT [Alice enters the room]

Client:  MSG Hi people!
Server:  CHAT Alice: Hi people!    (broadcast to all room members including sender)
```

---

## 5. Core Components

### Server.java

The server entry point. Opens a `ServerSocket` (or `SSLServerSocket`), accepts connections in a loop, and spawns a virtual thread for each one.

```java
try (var serverSocket = new ServerSocket(PORT)) {
    while (true) {
        Socket client = serverSocket.accept();
        Thread.ofVirtual().start(() -> new ClientHandler(client, sessionManager, roomManager).run());
    }
}
```

### ClientHandler.java

Handles the full lifecycle of one client connection: reading commands, dispatching to the correct manager, writing responses. Runs on a virtual thread. Must handle `IOException` gracefully (broken pipe = client disconnected).

### SessionManager.java

Maintains a `Map<String, Session>` of all active sessions keyed by token. Protected by a `ReentrantReadWriteLock` — reads (token lookup) are frequent; writes (new session, expiry) are rare.

```java
private final Map<String, Session> sessions = new HashMap<>();
private final ReadWriteLock lock = new ReentrantReadWriteLock();
```

### RoomManager.java

Maintains a `Map<String, Room>` of all rooms. Protected by a `ReentrantLock`. Responsible for creating new rooms and returning existing ones.

### Room.java

Holds the list of active subscribers (one per connected user in the room). `broadcast()` iterates over subscribers and pushes messages to their output queue. Each subscriber has a **bounded blocking queue** so that a slow client's queue fills up rather than blocking the broadcast.

```java
private final List<Subscriber> subscribers = new ArrayList<>();
private final ReentrantLock lock = new ReentrantLock();

public void broadcast(String message) {
    lock.lock();
    try {
        for (Subscriber s : subscribers) {
            s.offer(message); // non-blocking; drops message if queue full (slow client)
        }
    } finally {
        lock.unlock();
    }
}
```

### AIRoom.java

Extends `Room`. After every user message is broadcast, a background virtual thread calls Ollama with the full room context and the room's system prompt. When the response arrives, it is broadcast as `BOT: <response>`.

---

## 6. Concurrency Model

This is the most important graded aspect. The following rules apply:

### Virtual threads

Every client connection runs on a **Java 21 virtual thread** (`Thread.ofVirtual()`). This avoids platform thread overhead while allowing the simple blocking I/O pattern.

```java
Thread.ofVirtual().start(clientHandler);
```

### Locks (mandatory — no `java.util.concurrent` collections)

All shared state must be protected with classes from `java.util.concurrent.locks`:

| Shared resource | Lock type | Reason |
|---|---|---|
| `SessionManager.sessions` | `ReentrantReadWriteLock` | Many concurrent reads (token lookups) |
| `RoomManager.rooms` | `ReentrantLock` | Room creation/lookup must be atomic |
| `Room.subscribers` | `ReentrantLock` | Concurrent join/leave/broadcast |
| `UserStore` (file writes) | `ReentrantLock` | Prevent concurrent file corruption |

### Slow client isolation

Each client's outbound messages go through a **bounded `ArrayBlockingQueue`**. The broadcaster calls `queue.offer()` (non-blocking). If the queue is full, the message is silently dropped for that client only — other clients are unaffected. A dedicated writer thread per client drains the queue to the socket.

```
broadcast()         → offer() to each subscriber's queue   (non-blocking)
writer thread       → poll() from queue → write to socket   (blocking on I/O)
```

This ensures one slow client can never stall the broadcast for everyone else.

---

## 7. Authentication & Tokens

### Registration

User credentials can be loaded from a file (`data/users.txt`) at server startup, or the server can accept a `REGISTER` command. Passwords must be stored **hashed** (use `MessageDigest` with SHA-256 from `java.security`).

```
# users.txt format
alice:5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8
bob:6cf615d5bcaac778352a8f1f3360d23f02f34ec182e259897fd6ce485d7870d4
```

### Session token flow

```
1. Client sends:    AUTH alice secret123
2. Server verifies password hash
3. Server creates:  Session { username="alice", token=UUID, expiry=now+24h, currentRoom=null }
4. Server replies:  TOKEN <uuid-string>
5. Client stores token in memory (never on disk)
```

### Reconnection with token

```
1. TCP connection drops
2. Client detects broken pipe / read error
3. Client reconnects TCP (new socket)
4. Client sends:    TOKEN <stored-uuid>
5. Server looks up session by token
6. If valid and not expired: server resumes relaying room messages on new connection
7. Server replies:  OK (and optionally re-sends recent room messages)
```

### Token expiry

Store an expiry timestamp in the `Token` object. On lookup, reject tokens past their expiry and respond with `ERROR token-expired`, forcing the user to re-authenticate.

```java
public record Token(String value, String username, Instant expiry) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
```

---

## 8. Room System

### Creating and joining rooms

- `JOIN <name>` creates the room if it does not exist, then adds the user.
- If the name ends with a special marker (e.g. `AI:<name>`) or a `CREATEAI` command is used, an `AIRoom` is created with an associated system prompt.
- Users can only be in one room at a time. Joining a new room automatically leaves the current one.

### Message timeline

When a user joins a room, they should immediately start receiving new messages. Optionally, deliver the last N messages from the room's history on join so the user has context.

### Room listing

`LIST` returns all currently existing room names. AI rooms can be marked with a prefix (e.g. `[AI] Doodle`) so clients can distinguish them.

---

## 9. AI Rooms & Ollama

### What Ollama is

Ollama is a lightweight local LLM runner. It exposes a REST API on `http://localhost:11434`. Your Java code makes HTTP calls to it using `java.net.http.HttpClient` — no external libraries needed.

### Installing Ollama

Download from **https://ollama.com/download** (Windows, macOS, Linux).

On Linux:
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Pulling a model

```bash
ollama pull llama3.2        # ~2 GB, recommended
ollama pull llama3.2:1b     # ~800 MB, lighter option for slow machines
ollama pull mistral         # alternative, ~4 GB
```

### Verifying Ollama is running

```bash
ollama serve                # start the server (may already run as a system service)
ollama list                 # list downloaded models

# Manual test
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "Hello"}],
  "stream": false
}'
```

### Java → Ollama integration (`OllamaClient.java`)

Since only Java SE packages are allowed, build the JSON by hand and parse the response with simple string operations.

```java
import java.net.http.*;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class OllamaClient {

    private static final String URL  = "http://localhost:11434/api/chat";
    private static final String MODEL = "llama3.2";
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * Send full room conversation to Ollama and return the Bot reply.
     * @param systemPrompt  The AI room's configured prompt
     * @param messages      All room messages so far: [{role, content}, ...]
     */
    public String chat(String systemPrompt, List<Map<String, String>> messages) throws Exception {
        var allMessages = new StringBuilder("[");
        allMessages.append("{\"role\":\"system\",\"content\":\"")
                   .append(escape(systemPrompt)).append("\"},");
        for (var msg : messages) {
            allMessages.append("{\"role\":\"").append(msg.get("role"))
                       .append("\",\"content\":\"").append(escape(msg.get("content")))
                       .append("\"},");
        }
        // Remove trailing comma
        if (allMessages.charAt(allMessages.length() - 1) == ',')
            allMessages.deleteCharAt(allMessages.length() - 1);
        allMessages.append("]");

        String body = "{\"model\":\"" + MODEL + "\",\"messages\":" + allMessages + ",\"stream\":false}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return parseContent(response.body()); // extract message.content from JSON
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String parseContent(String json) {
        // Locate "content":"..." in the response JSON
        int idx = json.indexOf("\"content\":\"");
        if (idx == -1) return "[no response]";
        int start = idx + 11;
        int end = json.indexOf("\"", start);
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
}
```

### AIRoom behaviour

Every time a user message is added to an AI room:

1. The message is broadcast normally to all human users.
2. A virtual thread is spawned to call Ollama with the full message history + the room's system prompt.
3. When Ollama responds, the reply is broadcast as `BOT: <response>`.

This happens asynchronously so the Ollama call (which may take a few seconds) does not block other messages.

### Hardware requirements for Ollama

| Model | RAM needed | GPU needed |
|---|---|---|
| `llama3.2:1b` | 4–6 GB | No |
| `llama3.2` (3b) | 6–8 GB | No |
| `mistral` (7b) | 12–16 GB | Recommended |

For the assignment, `llama3.2:1b` is sufficient to demonstrate the feature.

---

## 10. Fault Tolerance & Reconnection

### Server side

The server must **never lose session state** when a TCP connection drops. Sessions are kept in memory (or optionally persisted to disk) independently of sockets. When a connection breaks:

1. The `ClientHandler` catches the `IOException`.
2. It marks the session's socket as `null` / disconnected.
3. The session (username, current room, token) remains in `SessionManager`.
4. New messages to the room are queued (bounded) for the user, or simply not delivered until reconnect.

### Client side

The `ConnectionManager` wraps socket creation in a retry loop:

```java
while (true) {
    try {
        socket = new Socket(HOST, PORT);
        // Send stored token to resume session
        out.println("TOKEN " + storedToken);
        String response = in.readLine();
        if (response.startsWith("OK")) break; // resumed successfully
    } catch (IOException e) {
        Thread.sleep(RETRY_DELAY_MS); // wait before retrying
    }
}
```

The client **never stores credentials** — only the in-memory token. If the token expires, the user is prompted to re-enter their password.

### What is preserved across reconnect

- Username and identity
- Current room membership
- Server-side session state

### What is not preserved

- Undelivered messages (optional to buffer these server-side)
- SSL handshake state (a new handshake happens on reconnect)

---

## 11. SSL / Secure Channels

Use `javax.net.ssl` to wrap the sockets in TLS. This is optional but recommended for good practice marks.

### Generate a self-signed certificate

```bash
keytool -genkeypair -alias chatserver \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -keystore certs/server.keystore \
  -storepass changeit

keytool -exportcert -alias chatserver \
  -keystore certs/server.keystore \
  -storepass changeit \
  -file certs/server.cer

keytool -importcert -alias chatserver \
  -file certs/server.cer \
  -keystore certs/client.truststore \
  -storepass changeit -noprompt
```

### Server-side SSL socket

```java
SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
```

Run with:
```bash
java -Djavax.net.ssl.keyStore=certs/server.keystore \
     -Djavax.net.ssl.keyStorePassword=changeit \
     chat.server.Server
```

### Client-side SSL socket

```java
SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);
```

Run with:
```bash
java -Djavax.net.ssl.trustStore=certs/client.truststore \
     -Djavax.net.ssl.trustStorePassword=changeit \
     chat.client.Client
```

---

## 12. Setup & Installation

### Requirements

| Tool | Version | Where to get |
|---|---|---|
| Java SE | 21 or newer | https://adoptium.net (Eclipse Temurin 21 LTS) |
| Ollama | Latest | https://ollama.com/download |
| LLM model | `llama3.2` or `llama3.2:1b` | `ollama pull llama3.2` |
| Git | Any | Pre-installed on most systems |

No build tool (Maven/Gradle) is needed — the assignment uses only Java SE packages. Compile with `javac` directly.

### Compile

```bash
find src -name "*.java" > sources.txt
javac -d out @sources.txt
```

---

## 13. Running the System

### Start the server

```bash
java -cp out chat.server.Server [port]
# Default port: 5000
```

With SSL:
```bash
java -Djavax.net.ssl.keyStore=certs/server.keystore \
     -Djavax.net.ssl.keyStorePassword=changeit \
     -cp out chat.server.Server
```

### Start a client

```bash
java -cp out chat.client.Client [host] [port]
# Default: localhost 5000
```

### Shell scripts

`scripts/start-server.sh`:
```bash
#!/bin/bash
java -cp out chat.server.Server ${1:-5000}
```

`scripts/start-client.sh`:
```bash
#!/bin/bash
java -cp out chat.client.Client ${1:-localhost} ${2:-5000}
```

### Example multi-client test

Open three terminals:

```bash
# Terminal 1
./scripts/start-server.sh

# Terminal 2
./scripts/start-client.sh
# > AUTH alice pass123
# > JOIN General

# Terminal 3
./scripts/start-client.sh
# > AUTH bob pass456
# > JOIN General
# > MSG Hello Alice!
```

---

## 14. Grading Criteria Summary

| Criterion | Key points |
|---|---|
| **Concurrency (most important)** | No race conditions; use `java.util.concurrent.locks` only; virtual threads; slow client isolation |
| **Functionality** | Auth, rooms, real-time broadcast, AI rooms, reconnect |
| **Fault tolerance** | Token-based reconnect; no credential caching on client; session survives TCP drop |
| **Code quality** | Clean separation of concerns; correct locking discipline; no deadlocks |
| **SSL (optional bonus)** | `javax.net.ssl` wrapping of server and client sockets |
| **README** | Clear instructions to compile and run server + clients |

### Common pitfalls to avoid

- Using `synchronized` on entire methods when a `ReadWriteLock` would allow more concurrency
- Storing passwords in plaintext (use SHA-256 hashing)
- Blocking the broadcast loop on slow socket writes (use per-client queues)
- Forgetting to remove a user from their room when they disconnect
- Sharing the same `HttpClient` instance across threads without verifying thread safety
- Not handling `InterruptedException` properly in reconnect loops
