# CPD 2025/2026 - Distributed Chat System

Initial repository scaffold for the Java SE 21 chat system assignment.

## How to run

This repository currently contains the project skeleton. Once the Java sources are added under `src/main/java`, compile them with:

```bash
cd /home/josebreis05/repos/g11/assign2
find src/main/java -name "*.java" > sources.txt
mkdir -p out
javac -d out @sources.txt
```

After compilation, start the server in one terminal:

```bash
java -cp out chat.server.Server [port]
```

The default port is `5000` if no port is provided.

In another terminal, start a client:

```bash
java -cp out chat.client.Client [host] [port]
```

The default host is `localhost` and the default port is `5000`.

You can also use the helper scripts from the project root:

```bash
./start-server.sh [port]
./start-client.sh [host] [port]
```

The scripts now compile the current Java sources into `out/` and launch the rough starter server and client.


