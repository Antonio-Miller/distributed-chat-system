package chat.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SessionPersistence {
    private final Path sessionsFile;

    SessionPersistence(Path sessionsFile) {
        this.sessionsFile = sessionsFile;
    }

    void save(Map<String, Session> byUsername) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# username:token:expiresAt");
        synchronized (byUsername) {
            for (Session session : byUsername.values()) {
                lines.add(session.username() + ":" + session.token() + ":" + session.expiresAt());
            }
        }
        Files.write(sessionsFile, lines, StandardCharsets.UTF_8);
    }

    Map<String, SessionData> load() throws IOException {
        Map<String, SessionData> sessions = new HashMap<>();
        if (!Files.exists(sessionsFile)) return sessions;

        List<String> lines = Files.readAllLines(sessionsFile, StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(":", 3);
            if (parts.length != 3) continue;

            String username = parts[0];
            String token = parts[1];
            try {
                Instant expiresAt = Instant.parse(parts[2]);
                long expiresAtMillis = expiresAt.toEpochMilli();
                if (expiresAtMillis > now) {
                    sessions.put(token, new SessionData(username, token, expiresAtMillis));
                }
            } catch (Exception e) {
                // Skip malformed entries
            }
        }
        return sessions;
    }

    static final class SessionData {
        final String username;
        final String token;
        final long expiresAtMillis;

        SessionData(String username, String token, long expiresAtMillis) {
            this.username = username;
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
