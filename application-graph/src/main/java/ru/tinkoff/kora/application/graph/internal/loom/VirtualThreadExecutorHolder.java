package ru.tinkoff.kora.application.graph.internal.loom;

import jakarta.annotation.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.Executor;

public class VirtualThreadExecutorHolder {
    @Nullable
    public static final Executor executor;

    static {
        executor = createExecutor();
    }

    @Nullable
    private static Executor createExecutor() {
        var loomEnabled = Boolean.parseBoolean(System.getProperty("kora.loom.enabled", "true"));
        if (!loomEnabled) {
            return null;
        }
        try {
            var lookup = MethodHandles.publicLookup();
            var virtualBuilder = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
            var ofVirtual = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(virtualBuilder));
            var start = lookup.findVirtual(virtualBuilder, "start", MethodType.methodType(Thread.class, Runnable.class));

            var builder = ofVirtual.invoke();

            Objects.requireNonNull(builder);
            Objects.requireNonNull(start);

            return runnable -> {
                try {
                    start.invoke(builder, runnable);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (Throwable t) {
            return null;
        }
    }
}
