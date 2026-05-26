package chat.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

final class RoomManager {
    private final Map<String, Room> rooms = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AiService aiService;

    RoomManager(AiService aiService) {
        this.aiService = aiService;
        rooms.put("General", new Room("General", false, "", aiService));
        rooms.put("Library", new Room("Library", false, "", aiService));
    }

    Room getOrCreateNormalForJoin(String name) {
        String cleanName = validateRoomName(name);
        lock.lock();
        try {
            Room existing = rooms.get(cleanName);
            if (existing != null) return existing;
            Room created = new Room(cleanName, false, "", aiService);
            rooms.put(cleanName, created);
            return created;
        } finally {
            lock.unlock();
        }
    }

    Room createAiRoom(String name, String prompt) {
        String cleanName = validateRoomName(name);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("AI room prompt cannot be empty");
        }
        lock.lock();
        try {
            if (rooms.containsKey(cleanName)) {
                throw new IllegalArgumentException("A room with that name already exists");
            }
            Room created = new Room(cleanName, true, prompt.strip(), aiService);
            rooms.put(cleanName, created);
            return created;
        } finally {
            lock.unlock();
        }
    }

    List<RoomInfo> listRooms() {
        lock.lock();
        try {
            List<RoomInfo> out = new ArrayList<>();
            for (Room room : rooms.values()) out.add(new RoomInfo(room.name(), room.isAiRoom()));
            out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return out;
        } finally {
            lock.unlock();
        }
    }

    static String validateRoomName(String name) {
        if (name == null) throw new IllegalArgumentException("Room name cannot be empty");
        String clean = name.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Room name cannot be empty");
        if (clean.length() > 80) throw new IllegalArgumentException("Room name is too long");
        if (clean.contains("|")) throw new IllegalArgumentException("Room name cannot contain '|'");
        return clean.replace('\r', ' ').replace('\n', ' ');
    }
}
