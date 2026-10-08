package dev.tacticalcombat.core;

import java.util.ArrayDeque;

/** Keeps recent presentation work; returns an evicted item so its result can still be logged. */
public final class BoundedQueue<T> {
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final int capacity;
    public BoundedQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public T add(T item) {
        T evicted = queue.size() == capacity ? queue.removeFirst() : null;
        queue.addLast(item); return evicted;
    }
    public T poll() { return queue.pollFirst(); }
    public boolean remove(T item) { return queue.remove(item); }
    public int size() { return queue.size(); }
    public void clear() { queue.clear(); }
}
