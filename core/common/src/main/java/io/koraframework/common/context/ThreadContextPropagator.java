package io.koraframework.common.context;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Captures framework context that must cross a thread boundary.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader}. A captured snapshot
 * belongs to the structured scope that captured it; each concurrent child receives a
 * {@linkplain Snapshot#fork() fork}.</p>
 */
public interface ThreadContextPropagator {

    /**
     * Captures the context currently bound to the calling thread.
     *
     * @return captured context, or {@code null} when this context is not bound
     */
    @Nullable
    Snapshot capture();

    interface Snapshot {
        /**
         * Creates a snapshot for an isolated concurrent child.
         */
        Snapshot fork();

        /**
         * Calls {@code operation} with this snapshot bound to the current thread.
         */
        <T> T call(Supplier<T> operation);

        /**
         * Runs {@code runnable} with this snapshot bound to the current thread.
         */
        default void run(Runnable runnable) {
            this.call(() -> {
                runnable.run();
                return null;
            });
        }
    }
}
