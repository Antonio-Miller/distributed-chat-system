package chat.client;

import chat.common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

public final class ChatClient {
    private final String host;
    private final int port;
    private final ReentrantLock connectionLock = new ReentrantLock();

    private Socket socket;
    private BufferedReader serverReader;
    private BufferedWriter serverWriter;
    private volatile boolean running = true;
    private volatile boolean reconnecting = false;
    private volatile String token;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "localhost";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 12345;
        ChatClient client = new ChatClient(host, port);
        client.start();
    }

    private void start() {
        try {
            connect();
            startReaderThread();
            printLocalHelp();
            readUserInputLoop();
        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port + " - " + e.getMessage());
        } finally {
            running = false;
            closeSocket();
        }
    }

    private void connect() throws IOException {
        connectionLock.lock();
        try {
            Socket newSocket = new Socket(host, port);
            newSocket.setTcpNoDelay(true);
            socket = newSocket;
            serverReader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
            serverWriter = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        } finally {
            connectionLock.unlock();
        }
    }

    private void startReaderThread() {
        Thread.startVirtualThread(this::serverReadLoop);
    }

    private void serverReadLoop() {
        try {
            while (running) {
                BufferedReader readerSnapshot;
                connectionLock.lock();
                try {
                    readerSnapshot = serverReader;
                } finally {
                    connectionLock.unlock();
                }
                String line = readerSnapshot.readLine();
                if (line == null) throw new IOException("connection closed");
                handleServerLine(line);
            }
        } catch (IOException e) {
            if (running) onDisconnected();
        }
    }

    private void handleServerLine(String line) {
        if (line.startsWith(Protocol.TOKEN + " ")) {
            token = line.substring((Protocol.TOKEN + " ").length()).strip();
            System.out.println("[session token received]");
        } else if (line.startsWith(Protocol.CHAT + " ")) {
            System.out.println(line.substring((Protocol.CHAT + " ").length()));
        } else if (line.startsWith(Protocol.ROOM + " ")) {
            String rest = line.substring((Protocol.ROOM + " ").length());
            int firstSpace = rest.indexOf(' ');
            if (firstSpace > 0) {
                String type = rest.substring(0, firstSpace);
                String name = rest.substring(firstSpace + 1);
                System.out.println("  - " + name + " [" + type + "]");
            } else {
                System.out.println("  - " + rest);
            }
        } else if (line.equals(Protocol.END_ROOMS)) {
            System.out.println("Use /join <room> to enter an existing room or create a normal one.");
        } else if (line.startsWith(Protocol.HISTORY_BEGIN + " ")) {
            System.out.println("--- history: " + line.substring((Protocol.HISTORY_BEGIN + " ").length()) + " ---");
        } else if (line.startsWith(Protocol.HISTORY_END + " ")) {
            System.out.println("--- live messages ---");
        } else if (line.startsWith(Protocol.JOINED + " ")) {
            System.out.println("Joined room: " + line.substring((Protocol.JOINED + " ").length()));
        } else if (line.startsWith(Protocol.LEFT + " ")) {
            System.out.println("Left room: " + line.substring((Protocol.LEFT + " ").length()));
        } else if (line.startsWith(Protocol.OK + " ")) {
            System.out.println(line.substring((Protocol.OK + " ").length()));
        } else if (line.startsWith(Protocol.ERR + " ")) {
            System.out.println("ERROR: " + line.substring((Protocol.ERR + " ").length()));
        } else {
            System.out.println(line);
        }
    }

    private void readUserInputLoop() {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (running && scanner.hasNextLine()) {
            String input = scanner.nextLine();
            if (input.isBlank()) continue;
            if (input.startsWith("/")) handleLocalCommand(input.strip());
            else sendLine("MSG " + input);
        }
    }

    private void handleLocalCommand(String input) {
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (command) {
            case "/login" -> sendLine("LOGIN " + arg);
            case "/register" -> sendLine("REGISTER " + arg);
            case "/rooms" -> sendLine("ROOMS");
            case "/join" -> sendLine("JOIN " + arg);
            case "/create" -> sendLine("CREATE " + arg);
            case "/createai" -> sendLine("CREATEAI " + arg);
            case "/joinai" -> sendLine("JOINAI " + arg);
            case "/leave" -> sendLine("LEAVE");
            case "/quit" -> {
                sendLine("QUIT");
                running = false;
                closeSocket();
            }
            case "/help" -> printLocalHelp();
            default -> System.out.println("Unknown local command. Type /help.");
        }
    }

    private void sendLine(String line) {
        connectionLock.lock();
        try {
            if (serverWriter == null) {
                System.out.println("Not connected.");
                return;
            }
            serverWriter.write(line);
            serverWriter.newLine();
            serverWriter.flush();
        } catch (IOException e) {
            System.out.println("Could not send message: " + e.getMessage());
            onDisconnected();
        } finally {
            connectionLock.unlock();
        }
    }

    private void onDisconnected() {
        if (token == null || token.isBlank()) {
            System.out.println("Disconnected before authentication. Restart the client or reconnect manually.");
            running = false;
            return;
        }
        if (reconnecting) return;
        reconnecting = true;
        Thread.startVirtualThread(() -> {
            closeSocket();
            System.out.println("Connection lost. Reconnecting with session token...");
            while (running) {
                try {
                    Thread.sleep(1000);
                    connect();
                    sendLine("RESUME " + token);
                    reconnecting = false;
                    startReaderThread();
                    return;
                } catch (IOException e) {
                    System.out.println("Reconnect failed: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    return;
                }
            }
        });
    }

    private void closeSocket() {
        connectionLock.lock();
        try {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // ignored
                }
            }
            socket = null;
            serverReader = null;
            serverWriter = null;
        } finally {
            connectionLock.unlock();
        }
    }

    private static void printLocalHelp() {
        System.out.println("Commands:");
        System.out.println("  /login <username> <password>");
        System.out.println("  /register <username> <password>");
        System.out.println("  /rooms");
        System.out.println("  /join <room name>       # joins existing room or creates normal room");
        System.out.println("  /create <room name>     # same as /join for normal rooms");
        System.out.println("  /createai <room>|<prompt>");
        System.out.println("  /leave");
        System.out.println("  /quit");
        System.out.println("Any other line is sent as a chat message after joining a room.");
        System.out.println("Default users: alice/1234, bob/1234, eve/1234");
    }
}
