package io.koraframework.netty.common.loom.queue;

public interface MessagePassingQueue<T> {
    int UNBOUNDED_CAPACITY = -1;

    interface Supplier<T> {
        T get();
    }

    interface Consumer<T> {
        void accept(T e);
    }

    interface WaitStrategy {
        int idle(int idleCounter);
    }

    interface ExitCondition {

        boolean keepRunning();
    }

    boolean offer(T e);

    T poll();

    T peek();

    int size();

    void clear();

    boolean isEmpty();

    int capacity();

    boolean relaxedOffer(T e);

    T relaxedPoll();

    T relaxedPeek();

    int drain(Consumer<T> c, int limit);

    int fill(Supplier<T> s, int limit);

    int drain(Consumer<T> c);

    int fill(Supplier<T> s);

    void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit);

    void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit);
}
