package io.koraframework.netty.common.loom.queue;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public interface MessagePassingQueue<T> {
    int UNBOUNDED_CAPACITY = -1;

    boolean offer(T e);

    @Nullable
    T poll();

    @Nullable
    T peek();

    int size();

    void clear();

    boolean isEmpty();

    default int capacity() {
        return UNBOUNDED_CAPACITY;
    }

    default boolean relaxedOffer(T e) {
        return offer(e);
    }

    @Nullable
    default T relaxedPoll() {
        return poll();
    }

    @Nullable
    default T relaxedPeek() {
        return peek();
    }

    int drain(Consumer<T> c, int limit);

    int drain(Consumer<T> c);
}
