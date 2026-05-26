package chat.server;

import chat.common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class ClientConnection implements Runnable {
    private final Socket socket;
    private final ServerState state;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    private volatile boolean closed;
    private Session session;

    ClientConnection(Socket socket, ServerState state) throws IOException {
        this.socket = socket;
        this.state = state;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            sendDirect(Protocol.OK + " Connected. Authenticate with LOGIN, REGISTER, or RESUME.");
            while (!closed) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.isBlank()) continue;
                if (session == null) handleUnauthenticated(line.strip());
                else handleAuthenticated(line.strip());
            }
        } catch (IOException e) {
            // Broken TCP connections are expected. The session is preserved.
        } finally {
            closeAndDetach();
        }
    }

    private void handleUnauthenticated(String line) throws IOException {
        String[] parts = line.split(" ", 3);
        String command = parts[0].toUpperCase();
        switch (command) {
            case "LOGIN" -> {
                if (parts.length < 3) {
                    sendDirect(Protocol.ERR + " Usage: LOGIN <username> <password>");
                    return;
                }
                String username = parts[1];
                char[] password = parts[2].toCharArray();
                if (!state.auth().authenticate(username, password)) {
                    sendDirect(Protocol.ERR + " Invalid username or password.");
                    return;
                }
                Session authenticated = state.sessions().createOrRefresh(username);
                bindAuthenticatedSession(authenticated, false);
            }
            case "REGISTER" -> {
                if (parts.length < 3) {
                    sendDirect(Protocol.ERR + " Usage: REGISTER <username> <password>");
                    return;
                }
                String username = parts[1];
                char[] password = parts[2].toCharArray();
                try {
                    if (!state.auth().register(username, password)) {
                        sendDirect(Protocol.ERR + " Could not register. Use a unique username with 3-32 letters/digits/._- characters.");
                        return;
                    }
                } catch (IOException e) {
                    sendDirect(Protocol.ERR + " Registration failed: " + e.getMessage());
                    return;
                }
                Session authenticated = state.sessions().createOrRefresh(username);
                bindAuthenticatedSession(authenticated, false);
            }
            case "RESUME" -> {
                if (parts.length < 2) {
                    sendDirect(Protocol.ERR + " Usage: RESUME <token>");
                    return;
                }
                Session resumed = state.sessions().resume(parts[1]);
                if (resumed == null) {
                    sendDirect(Protocol.ERR + " Invalid or expired token. Please login again.");
                    return;
                }
                bindAuthenticatedSession(resumed, true);
            }
            default -> sendDirect(Protocol.ERR + " Authenticate first with LOGIN, REGISTER, or RESUME.");
        }
    }

    private void bindAuthenticatedSession(Session authenticated, boolean resumed) throws IOException {
        this.session = authenticated;
        sendDirect(Protocol.TOKEN + " " + authenticated.token());
        Room room = authenticated.currentRoom();
        if (resumed) {
            sendDirect(Protocol.OK + " Resumed as " + authenticated.username()
                    + (room == null ? "." : " in room '" + room.name() + "'."));
        } else {
            sendDirect(Protocol.OK + " Authenticated as " + authenticated.username()
                    + ". Token expires at " + authenticated.expiresAt() + ".");
        }
        authenticated.attach(this);
        startWriter();
        if (!resumed) sendRooms(authenticated);
    }

    private void handleAuthenticated(String line) {
        String[] parts = line.split(" ", 2);
        String command = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1] : "";
        switch (command) {
            case "ROOMS" -> sendRooms(session);
            case "JOIN", "CREATE" -> joinRoom(arg);
            case "CREATEAI", "JOINAI" -> createAiAndJoin(arg);
            case "MSG" -> postMessage(arg);
            case "LEAVE" -> leaveCurrentRoom();
            case "QUIT" -> {
                leaveCurrentRoom();
                session.enqueue(Protocol.OK + " Bye.");
                closeSilently();
            }
            case "HELP" -> sendHelp(session);
            default -> session.enqueue(Protocol.ERR + " Unknown command. Type HELP.");
        }
    }

    private void sendRooms(Session target) {
        List<RoomInfo> rooms = state.rooms().listRooms();
        if (rooms.isEmpty()) {
            target.enqueue(Protocol.OK + " No rooms available.");
        } else {
            target.enqueue(Protocol.OK + " Available rooms:");
            for (RoomInfo info : rooms) {
                target.enqueue(Protocol.ROOM + " " + (info.aiRoom() ? "AI" : "NORMAL") + " " + info.name());
            }
        }
        target.enqueue(Protocol.END_ROOMS);
    }

    private void sendHelp(Session target) {
        target.enqueue(Protocol.OK + " Commands: ROOMS | JOIN <room> | CREATE <room> | CREATEAI <room>|<prompt> | MSG <text> | LEAVE | QUIT");
    }

    private void joinRoom(String roomName) {
        try {
            Room newRoom = state.rooms().getOrCreateNormalForJoin(roomName);
            Room oldRoom = session.currentRoom();
            if (oldRoom == newRoom) {
                session.enqueue(Protocol.OK + " Already in room '" + newRoom.name() + "'.");
                return;
            }
            if (oldRoom != null) oldRoom.leave(session, false);
            newRoom.join(session);
        } catch (IllegalArgumentException e) {
            session.enqueue(Protocol.ERR + " " + e.getMessage());
        }
    }

    private void createAiAndJoin(String arg) {
        int sep = arg.indexOf('|');
        if (sep < 0) {
            session.enqueue(Protocol.ERR + " Usage: CREATEAI <room name>|<prompt>");
            return;
        }
        String roomName = arg.substring(0, sep).strip();
        String prompt = arg.substring(sep + 1).strip();
        try {
            Room oldRoom = session.currentRoom();
            Room room = state.rooms().createAiRoom(roomName, prompt);
            if (oldRoom != null) oldRoom.leave(session, false);
            room.join(session);
        } catch (IllegalArgumentException e) {
            session.enqueue(Protocol.ERR + " " + e.getMessage());
        }
    }

    private void postMessage(String text) {
        Room room = session.currentRoom();
        if (room == null) {
            session.enqueue(Protocol.ERR + " Join a room before sending messages.");
            return;
        }
        room.postUserMessage(session, text);
    }

    private void leaveCurrentRoom() {
        Room room = session.currentRoom();
        if (room == null) {
            session.enqueue(Protocol.ERR + " You are not in a room.");
            return;
        }
        room.leave(session, true);
    }

    private void startWriter() {
        Thread.startVirtualThread(this::writeLoop);
    }

    private void writeLoop() {
        while (!closed && session != null && session.isAttached(this)) {
            String line = null;
            try {
                line = session.outbox().poll(1000, () -> !closed && session.isAttached(this));
                if (line == null) continue;
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                if (line != null && session != null) session.outbox().offerFirst(line);
                break;
            }
        }
        closeAndDetach();
    }

    private void sendDirect(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    synchronized void closeSilently() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // ignored
        }
        if (session != null) session.outbox().wakeAll();
    }

    private synchronized void closeAndDetach() {
        if (!closed) closeSilently();
        if (session != null) session.detach(this);
    }
}
