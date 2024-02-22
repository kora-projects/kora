package ru.tinkoff.kora.application.graph.internal.loom;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class VirtualThreadExecutorHolder {

    public enum VirtualThreadStatus {
        DISABLED,
        ENABLED,
        UNAVAILABLE
    }

    private VirtualThreadExecutorHolder() {}

    @Nullable
    private static final Executor executor;

    @Nullable
    private static final ExecutorService executorService;
    private static final VirtualThreadStatus status;

    static {
        var loomEnabled = System.getProperty("kora.loom.enabled");
        if (loomEnabled != null && !Boolean.parseBoolean(loomEnabled)) {
            executor = null;
            executorService = null;
            status = VirtualThreadStatus.DISABLED;
        } else {
            if (loomEnabled == null) {
                loomEnabled = "true"; // enabled by default
            }

            if (Boolean.parseBoolean(loomEnabled)) {
                final ThreadFactory loomThreadFactory = createLoomThreadFactory("E-VThread-");
                executor = createExecutor(loomThreadFactory);
                final ThreadFactory loomServiceFactory = createLoomThreadFactory("ES-VThread-");
                executorService = createExecutorService(loomServiceFactory);
                if (executor != null) {
                    status = VirtualThreadStatus.ENABLED;
                } else {
                    status = VirtualThreadStatus.UNAVAILABLE;
                }
            } else {
                executor = null;
                executorService = null;
                status = VirtualThreadStatus.UNAVAILABLE;
            }
        }
    }

    @Nonnull
    public static VirtualThreadStatus status() {
        return status;
    }

    @Nullable
    public static Executor executor() {
        return executor;
    }

    @Nullable
    public static ExecutorService executorService() {
        return executorService;
    }

    @Nullable
    private static ThreadFactory createLoomThreadFactory(String name) {
        try {
            var lookup = MethodHandles.publicLookup();
            var classLoomBuilder = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
            var methodLoomBuilder = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(classLoomBuilder));
            var methodLoomName = lookup.findVirtual(classLoomBuilder, "name", MethodType.methodType(classLoomBuilder, String.class, long.class));
            var methodLoomFactory = lookup.findVirtual(classLoomBuilder, "factory", MethodType.methodType(ThreadFactory.class));

            var builder = methodLoomBuilder.invoke();
            builder = methodLoomName.invoke(builder, name, 1);
            final ThreadFactory loomFactory = (ThreadFactory) methodLoomFactory.invoke(builder);

            return Objects.requireNonNull(loomFactory);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Executor createExecutor(@Nullable ThreadFactory loomThreadFactory) {
        if (loomThreadFactory == null) {
            return null;
        }

        return runnable -> {
            try {
                Thread thread = loomThreadFactory.newThread(runnable);
                thread.start();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Nullable
    private static ExecutorService createExecutorService(@Nullable ThreadFactory loomThreadFactory) {
        if (loomThreadFactory == null) {
            return null;
        }

        try {
            final Method methodLoomExecutor = Arrays.stream(Executors.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("newThreadPerTaskExecutor"))
                .findFirst()
                .orElseThrow();

            methodLoomExecutor.setAccessible(true);
            return ((ExecutorService) methodLoomExecutor.invoke(null, loomThreadFactory));
        } catch (Throwable t) {
            return null;
        }
    }
}
