package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.List;

public final class PrometheusMeterRegistryWrapper implements Lifecycle, Wrapped<MeterRegistry> {
    private final List<PrometheusMeterRegistryInitializer> initializers;
    private volatile PrometheusMeterRegistry registry;
    private volatile JvmGcMetrics gcMetrics;

    public PrometheusMeterRegistryWrapper(List<PrometheusMeterRegistryInitializer> initializers) {
        this.initializers = initializers;
    }

    @Override
    public void init() throws Exception {
        var meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        for (var initializer : initializers) {
            meterRegistry = initializer.apply(meterRegistry);
        }
        this.gcMetrics = new JvmGcMetrics();
        new ClassLoaderMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        this.gcMetrics.bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new FileDescriptorMetrics().bindTo(meterRegistry);
        new UptimeMetrics().bindTo(meterRegistry);
        this.registry = meterRegistry;
        Metrics.addRegistry(this.registry);
    }

    @Override
    public void release() {
        var r = this.registry;
        var gcMetrics = this.gcMetrics;
        try {
            if (gcMetrics != null) {
                gcMetrics.close();
            }
        } finally {
            if (r != null) {
                this.registry = null;
                Metrics.removeRegistry(r);
                r.close();
            }
        }
    }

    @Override
    public PrometheusMeterRegistry value() {
        return this.registry;
    }
}
