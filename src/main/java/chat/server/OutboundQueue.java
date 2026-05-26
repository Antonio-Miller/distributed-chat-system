package chat.server;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

final class OutboundQueue {
    private static final int DEFAULT_CAPACITY = 512;

    private final int capacity;
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    OutboundQueue() {
        this(DEFAULT_CAPACITY);
    }

    OutboundQueue(int capacity) {
        if (capacity < 8) throw new IllegalArgumentException("capacity must be >= 8");
        this.capacity = capacity;
    }

    void offer(String line) {
        lock.lock();
        try {
            if (queue.size() >= capacity) {
                queue.removeFirst();
                if (queue.size() >= capacity) queue.removeFirst();
                queue.addLast("ERR Your client is receiving messages too slowly; oldest pending messages were dropped.");
            }
            queue.addLast(line);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    void offerFirst(String line) {
        lock.lock();
        try {
            if (queue.size() >= capacity) queue.removeLast();
            queue.addFirst(line);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    String poll(long timeoutMillis, BooleanSupplier keepWaiting) throws InterruptedException {
        long nanos = timeoutMillis * 1_000_000L;
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty() && keepWaiting.getAsBoolean()) {
                if (nanos <= 0L) return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            if (queue.isEmpty()) return null;
            return queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    void wakeAll() {
        lock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
