package chat.server;

import java.io.IOException;
import java.nio.file.Path;

final class ServerState {
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AiService aiService = new AiService();
    private final RoomManager roomManager = new RoomManager(aiService);

    ServerState(Path usersFile) throws IOException {
        this.authService = new AuthService(usersFile);
        Path sessionsFile = usersFile.getParent().resolve("sessions.db");
        this.sessionManager = new SessionManager(sessionsFile);
    }

    AuthService auth() {
        return authService;
    }

    SessionManager sessions() {
        return sessionManager;
    }

    RoomManager rooms() {
        return roomManager;
    }
}
