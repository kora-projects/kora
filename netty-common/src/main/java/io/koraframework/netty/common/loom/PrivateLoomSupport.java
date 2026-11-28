package io.koraframework.netty.common.loom;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

public final class PrivateLoomSupport {
    private static final ForkJoinPool DEFAULT_SCHEDULER_POOL;
    private static final MethodHandle BUILDER_SCHEDULER;
    private static final MethodHandle CARRIER_THREAD;
    private static final MethodHandle THREAD_SCHEDULER;
    private static final boolean SUPPORTED;
    private static final boolean CARRIER_THREAD_SUPPORTED;
    private static final Throwable FAILURE;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        ForkJoinPool defaultSchedulerPool;
        MethodHandle builderScheduler;
        MethodHandle threadScheduler;
        Throwable failure;

        try {
            Field defaultSchedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("DEFAULT_SCHEDULER");
            defaultSchedulerField.setAccessible(true);
            defaultSchedulerPool = (ForkJoinPool) defaultSchedulerField.get(null);

            Field schedulerField = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder")
                .getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            builderScheduler = lookup.unreflectSetter(schedulerField)
                .asType(MethodType.methodType(void.class, Object.class, Executor.class));

            Field threadSchedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("scheduler");
            threadSchedulerField.setAccessible(true);
            threadScheduler = lookup.unreflectGetter(threadSchedulerField)
                .asType(MethodType.methodType(Executor.class, Thread.class));

            failure = null;
        } catch (ReflectiveOperationException | InaccessibleObjectException roe) {
            defaultSchedulerPool = null;
            builderScheduler = null;
            threadScheduler = null;
            failure = roe;
        }
        DEFAULT_SCHEDULER_POOL = defaultSchedulerPool;
        BUILDER_SCHEDULER = builderScheduler;
        THREAD_SCHEDULER = threadScheduler;
        SUPPORTED = threadScheduler != null;
        FAILURE = failure;

        MethodHandle carrierThread;
        try {
            Field carrierThreadField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("carrierThread");
            carrierThreadField.setAccessible(true);
            carrierThread = lookup.unreflectGetter(carrierThreadField)
                .asType(MethodType.methodType(Thread.class, Thread.class));
        } catch (Exception e) {
            carrierThread = null;
        }
        CARRIER_THREAD = carrierThread;
        CARRIER_THREAD_SUPPORTED = carrierThread != null;
    }

    public static ForkJoinPool getDefaultScheduler() {
        return DEFAULT_SCHEDULER_POOL;
    }

    public static void setScheduler(Object builder, Executor executor) {
        try {
            BUILDER_SCHEDULER.invokeExact(builder, executor);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    public static Thread getCarrierThread(Thread t) {
        try {
            return (Thread) CARRIER_THREAD.invokeExact(t);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    public static Executor getScheduler(Thread t) {
        try {
            return (Executor) THREAD_SCHEDULER.invokeExact(t);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    public static boolean isSupported() {
        return SUPPORTED;
    }

    public static boolean isCarrierThreadSupported() {
        return CARRIER_THREAD_SUPPORTED;
    }

    public static boolean isCarrierThreadSupportedCheck() {
        if (!SUPPORTED && !LoomBranchSupport.isSupported()) {
            throw new IllegalStateException("Failed to access loom internals. Please make sure to add the `--add-opens=java.base/java.lang=ALL-UNNAMED` JVM argument. (" + FAILURE + ")");
        }
        return true;
    }

    private static RuntimeException rethrow(Throwable e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        if (e instanceof Error err) {
            throw err;
        }
        return new RuntimeException(e);
    }
}
