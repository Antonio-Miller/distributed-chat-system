package chat.server;

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
                return existing;
            }
            String token = newTokenLocked();
            Session session = new Session(username, token, expiresAt);
            byUsername.put(username, session);
            byToken.put(token, session);
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
                return null;
            }
            session.setToken(token, now + TOKEN_TTL.toMillis());
            return session;
        } finally {
            lock.unlock();
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
