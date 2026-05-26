package chat.server;

import java.io.IOException;
import java.nio.file.Path;

final class ServerState {
    private final AuthService authService;
    private final SessionManager sessionManager = new SessionManager();
    private final AiService aiService = new AiService();
    private final RoomManager roomManager = new RoomManager(aiService);

    ServerState(Path usersFile) throws IOException {
        this.authService = new AuthService(usersFile);
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
