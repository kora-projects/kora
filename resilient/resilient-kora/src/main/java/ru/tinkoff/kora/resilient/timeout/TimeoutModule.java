package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public interface TimeoutModule {

    default TimeoutConfig koraTimeoutConfig(Config config, ConfigValueExtractor<TimeoutConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default TimeoutManager koraTimeoutManager(TimeoutExecutor timeoutExecutor,
                                              TimeoutConfig config,
                                              @Nullable TimeoutMetrics metrics) {
        TimeoutMetrics timeoutMetrics = (metrics == null) ? new NoopTimeoutMetrics() : metrics;
        return new KoraTimeoutManager(timeoutMetrics, timeoutExecutor, config);
    }

    @DefaultComponent
    default TimeoutExecutor koraTimeoutExecutorService() {
        return (VirtualThreadExecutorHolder.status() == VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED)
            ? createVirtualExecutorService()
            : new TimeoutExecutor(Executors.newCachedThreadPool());
    }

    @Nullable
    private static TimeoutExecutor createVirtualExecutorService() {

        class VirtualTimeoutExecutor extends TimeoutExecutor {

            public VirtualTimeoutExecutor(ExecutorService executorService) {
                super(executorService);
            }

            @Override
            public void init() {
                // do nothing
            }

            @Override
            public void release() {
                // do nothing
            }
        }

        try {
            var lookup = MethodHandles.publicLookup();
            var methodLoomExecutor = lookup.findStatic(Executors.class, "newThreadPerTaskExecutor", MethodType.methodType(ExecutorService.class, ThreadFactory.class));
            var classLoomBuilder = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
            var methodLoomBuilder = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(classLoomBuilder));
            var methodLoomName = lookup.findVirtual(classLoomBuilder, "name", MethodType.methodType(classLoomBuilder, String.class, long.class));
            var methodLoomFactory = lookup.findVirtual(classLoomBuilder, "factory", MethodType.methodType(ThreadFactory.class));

            var builder = methodLoomBuilder.invoke();
            builder = methodLoomName.invoke(builder, "TES-VThread-", 1);
            final ThreadFactory loomFactory = (ThreadFactory) methodLoomFactory.invoke(builder);
            ExecutorService executor = (ExecutorService) methodLoomExecutor.invoke(Objects.requireNonNull(loomFactory));
            return new VirtualTimeoutExecutor(executor);
        } catch (Throwable t) {
            return null;
        }
    }
}
