package chat.server;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

final class SessionManager {
    private static final Duration TOKEN_TTL = Duration.ofHours(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> byToken = new HashMap<>();
    private final Map<String, Session> byUsername = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final SessionPersistence persistence;

    SessionManager(Path sessionsFile) throws IOException {
        this.persistence = new SessionPersistence(sessionsFile);
        loadSessions();
    }

    private void loadSessions() throws IOException {
        lock.lock();
        try {
            Map<String, SessionPersistence.SessionData> loaded = persistence.load();
            for (SessionPersistence.SessionData data : loaded.values()) {
                Session session = new Session(data.username, data.token, data.expiresAtMillis);
                byUsername.put(data.username, session);
                byToken.put(data.token, session);
            }
            System.out.println("Loaded " + loaded.size() + " persisted sessions.");
        } finally {
            lock.unlock();
        }
    }

    Session createOrRefresh(String username) {
        lock.lock();
        try {
            long expiresAt = System.currentTimeMillis() + TOKEN_TTL.toMillis();
            Session existing = byUsername.get(username);
            if (existing != null) {
                byToken.remove(existing.token());
                String token = newTokenLocked();
                existing.setToken(token, expiresAt);
                byToken.put(token, existing);
                persistLocked();
                return existing;
            }
            String token = newTokenLocked();
            Session session = new Session(username, token, expiresAt);
            byUsername.put(username, session);
            byToken.put(token, session);
            persistLocked();
            return session;
        } finally {
            lock.unlock();
        }
    }

    Session resume(String token) {
        lock.lock();
        try {
            Session session = byToken.get(token);
            if (session == null) return null;
            long now = System.currentTimeMillis();
            if (session.isExpired(now)) {
                byToken.remove(token);
                byUsername.remove(session.username());
                persistLocked();
                return null;
            }
            session.setToken(token, now + TOKEN_TTL.toMillis());
            persistLocked();
            return session;
        } finally {
            lock.unlock();
        }
    }

    private void persistLocked() {
        try {
            persistence.save(byUsername);
        } catch (IOException e) {
            System.err.println("Failed to persist sessions: " + e.getMessage());
        }
    }

    private String newTokenLocked() {
        while (true) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = PasswordUtil.toHex(bytes);
            if (!byToken.containsKey(token)) return token;
        }
    }
}
