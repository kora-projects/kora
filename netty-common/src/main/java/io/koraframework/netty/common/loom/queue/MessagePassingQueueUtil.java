package io.koraframework.netty.common.loom.queue;

import java.util.function.Consumer;

public final class MessagePassingQueueUtil {

    private MessagePassingQueueUtil() {}

    public static <E> int drain(MessagePassingQueue<E> queue, Consumer<E> c, int limit) {
        if (c == null) {
            throw new IllegalArgumentException("c is null");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit is negative: " + limit);
        }
        if (limit == 0) {
            return 0;
        }
        E e;
        int i = 0;
        for (; i < limit && (e = queue.relaxedPoll()) != null; i++) {
            c.accept(e);
        }
        return i;
    }

    public static <E> int drain(MessagePassingQueue<E> queue, Consumer<E> c) {
        if (c == null) {
            throw new IllegalArgumentException("c is null");
        }
        E e;
        int i = 0;
        while ((e = queue.relaxedPoll()) != null) {
            i++;
            c.accept(e);
        }
        return i;
    }
}
