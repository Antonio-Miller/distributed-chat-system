package chat.server;

import chat.common.Protocol;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

final class Room {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String name;
    private final boolean aiRoom;
    private final String aiPrompt;
    private final AiService aiService;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<String> timeline = new ArrayList<>();
    private final Set<Session> members = new HashSet<>();

    Room(String name, boolean aiRoom, String aiPrompt, AiService aiService) {
        this.name = name;
        this.aiRoom = aiRoom;
        this.aiPrompt = aiPrompt;
        this.aiService = aiService;
    }

    String name() {
        return name;
    }

    boolean isAiRoom() {
        return aiRoom;
    }

    void join(Session session) {
        List<String> historySnapshot;
        List<Session> others;
        String systemLine = systemLine(session.username() + " enters the room");

        lock.lock();
        try {
            boolean added = members.add(session);
            if (added) timeline.add(systemLine);
            historySnapshot = new ArrayList<>(timeline);
            others = new ArrayList<>(members);
            others.remove(session);
        } finally {
            lock.unlock();
        }

        session.setCurrentRoom(this);
        session.enqueue(Protocol.JOINED + " " + name);
        session.enqueue(Protocol.HISTORY_BEGIN + " " + name);
        for (String line : historySnapshot) session.enqueue(Protocol.CHAT + " " + line);
        session.enqueue(Protocol.HISTORY_END + " " + name);
        for (Session other : others) other.enqueue(Protocol.CHAT + " " + systemLine);
    }

    void leave(Session session, boolean updateSessionRoom) {
        List<Session> remaining;
        String systemLine = systemLine(session.username() + " leaves the room");
        boolean wasMember;

        lock.lock();
        try {
            wasMember = members.remove(session);
            if (!wasMember) return;
            timeline.add(systemLine);
            remaining = new ArrayList<>(members);
        } finally {
            lock.unlock();
        }

        if (updateSessionRoom) session.setCurrentRoom(null);
        session.enqueue(Protocol.LEFT + " " + name);
        for (Session member : remaining) member.enqueue(Protocol.CHAT + " " + systemLine);
    }

    void postUserMessage(Session sender, String text) {
        if (text == null || text.isBlank()) return;
        String cleanText = oneLine(text.strip());
        String chatLine = timestamp() + " " + sender.username() + ": " + cleanText;
        List<Session> recipients;
        List<String> aiContext = null;

        lock.lock();
        try {
            if (!members.contains(sender)) {
                sender.enqueue(Protocol.ERR + " You are not in this room.");
                return;
            }
            timeline.add(chatLine);
            recipients = new ArrayList<>(members);
            if (aiRoom) aiContext = new ArrayList<>(timeline);
        } finally {
            lock.unlock();
        }

        for (Session recipient : recipients) recipient.enqueue(Protocol.CHAT + " " + chatLine);

        if (aiRoom) {
            List<String> contextForBot = aiContext;
            Thread.startVirtualThread(() -> {
                String reply = aiService.ask(aiPrompt, contextForBot);
                addBotMessage(reply);
            });
        }
    }

    private void addBotMessage(String reply) {
        String cleanReply = oneLine(reply == null ? "" : reply.strip());
        if (cleanReply.isBlank()) cleanReply = "[empty response]";
        String botLine = timestamp() + " Bot: " + cleanReply;
        List<Session> recipients;

        lock.lock();
        try {
            timeline.add(botLine);
            recipients = new ArrayList<>(members);
        } finally {
            lock.unlock();
        }

        for (Session recipient : recipients) recipient.enqueue(Protocol.CHAT + " " + botLine);
    }

    private static String systemLine(String text) {
        return timestamp() + " [" + text + "]";
    }

    private static String timestamp() {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "]";
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ');
    }
}
