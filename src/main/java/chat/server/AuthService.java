package chat.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

final class AuthService {
    private final Path usersFile;
    private final Map<String, UserRecord> users = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    AuthService(Path usersFile) throws IOException {
        this.usersFile = usersFile;
        load();
        ensureDefaultUsers();
    }

    boolean authenticate(String username, char[] password) {
        lock.lock();
        try {
            UserRecord record = users.get(username);
            return record != null && record.matches(password);
        } finally {
            clear(password);
            lock.unlock();
        }
    }

    boolean register(String username, char[] password) throws IOException {
        if (!isValidUsername(username)) {
            clear(password);
            return false;
        }
        lock.lock();
        try {
            if (users.containsKey(username)) return false;
            byte[] salt = PasswordUtil.newSalt();
            UserRecord record = new UserRecord(
                    username,
                    PasswordUtil.toHex(salt),
                    PasswordUtil.hashPassword(password, salt)
            );
            users.put(username, record);
            persistLocked();
            return true;
        } finally {
            clear(password);
            lock.unlock();
        }
    }

    static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_.-]{3,32}");
    }

    private void load() throws IOException {
        lock.lock();
        try {
            users.clear();
            if (!Files.exists(usersFile)) {
                Path parent = usersFile.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.createFile(usersFile);
                return;
            }
            for (String raw : Files.readAllLines(usersFile, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 3);
                if (parts.length != 3) continue;
                users.put(parts[0], new UserRecord(parts[0], parts[1], parts[2]));
            }
        } finally {
            lock.unlock();
        }
    }

    private void ensureDefaultUsers() throws IOException {
        lock.lock();
        try {
            boolean changed = false;
            changed |= addDefaultUserLocked("alice", "1234");
            changed |= addDefaultUserLocked("bob", "1234");
            changed |= addDefaultUserLocked("eve", "1234");
            if (changed) persistLocked();
        } finally {
            lock.unlock();
        }
    }

    private boolean addDefaultUserLocked(String username, String password) {
        if (users.containsKey(username)) return false;
        byte[] salt = PasswordUtil.newSalt();
        users.put(username, new UserRecord(
                username,
                PasswordUtil.toHex(salt),
                PasswordUtil.hashPassword(password.toCharArray(), salt)
        ));
        return true;
    }

    private void persistLocked() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# username:saltHex:sha256(salt+password)");
        for (UserRecord record : users.values()) {
            lines.add(record.username() + ":" + record.saltHex() + ":" + record.passwordHashHex());
        }
        Files.write(usersFile, lines, StandardCharsets.UTF_8);
    }

    private static void clear(char[] password) {
        if (password == null) return;
        for (int i = 0; i < password.length; i++) password[i] = '\0';
    }
}
