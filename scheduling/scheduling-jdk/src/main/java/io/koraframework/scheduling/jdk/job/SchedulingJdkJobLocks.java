package io.koraframework.scheduling.jdk.job;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Execution locks of the JDK jobs of one application graph, one per job method.
 * <p>
 * On graph refresh a replaced job is initialized before the old instance is released. Both instances take the same
 * lock from this object, so the new instance's first run waits for the old instance's running execution.
 * {@link io.koraframework.scheduling.jdk.SchedulingJdkModule} provides it without dependencies, so a refresh never
 * recreates it, and another graph in the same JVM gets its own.
 */
public final class SchedulingJdkJobLocks {

    // Entries are never removed: one per job method.
    private final ConcurrentHashMap<List<Object>, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * @return the execution lock of the job declared by the method {@code jobMethod} of {@code jobClass}
     */
    public ReentrantLock get(Class<?> jobClass, String jobMethod) {
        return this.locks.computeIfAbsent(List.of(jobClass, jobMethod), _ -> new ReentrantLock(true));
    }
}
