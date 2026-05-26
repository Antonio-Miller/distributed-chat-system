package chat.server;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

final class Session {
    private final String username;
    private final OutboundQueue outbox = new OutboundQueue();
    private final ReentrantLock lock = new ReentrantLock();

    private String token;
    private long expiresAtMillis;
    private Room currentRoom;
    private ClientConnection connection;

    Session(String username, String token, long expiresAtMillis) {
        this.username = username;
        this.token = token;
        this.expiresAtMillis = expiresAtMillis;
    }

    String username() {
        return username;
    }

    String token() {
        lock.lock();
        try {
            return token;
        } finally {
            lock.unlock();
        }
    }

    void setToken(String token, long expiresAtMillis) {
        lock.lock();
        try {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        } finally {
            lock.unlock();
        }
    }

    boolean isExpired(long nowMillis) {
        lock.lock();
        try {
            return nowMillis > expiresAtMillis;
        } finally {
            lock.unlock();
        }
    }

    Instant expiresAt() {
        lock.lock();
        try {
            return Instant.ofEpochMilli(expiresAtMillis);
        } finally {
            lock.unlock();
        }
    }

    void attach(ClientConnection newConnection) {
        ClientConnection oldConnection = null;
        lock.lock();
        try {
            if (connection != null && connection != newConnection) oldConnection = connection;
            connection = newConnection;
        } finally {
            lock.unlock();
        }
        if (oldConnection != null) oldConnection.closeSilently();
        outbox.wakeAll();
    }

    void detach(ClientConnection oldConnection) {
        lock.lock();
        try {
            if (connection == oldConnection) connection = null;
        } finally {
            lock.unlock();
        }
        outbox.wakeAll();
    }

    boolean isAttached(ClientConnection expectedConnection) {
        lock.lock();
        try {
            return connection == expectedConnection;
        } finally {
            lock.unlock();
        }
    }

    Room currentRoom() {
        lock.lock();
        try {
            return currentRoom;
        } finally {
            lock.unlock();
        }
    }

    void setCurrentRoom(Room room) {
        lock.lock();
        try {
            this.currentRoom = room;
        } finally {
            lock.unlock();
        }
    }

    void enqueue(String line) {
        outbox.offer(line);
    }

    OutboundQueue outbox() {
        return outbox;
    }
}
