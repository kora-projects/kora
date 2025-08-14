package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.Gauge;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

public final class PrometheusMeterRegistryWrapper implements Lifecycle, Wrapped<PrometheusMeterRegistry> {

    private static final String KORA_VERSION = readVersion();

    private final List<PrometheusMeterRegistryInitializer> initializers;
    private final Supplier<PrometheusMeterRegistry> registrySupplier;

    private volatile PrometheusMeterRegistry registry;
    private volatile JvmGcMetrics gcMetrics;
    private volatile Gauge koraVersionMetric;

    public PrometheusMeterRegistryWrapper(List<PrometheusMeterRegistryInitializer> initializers) {
        this(initializers, () -> new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    public PrometheusMeterRegistryWrapper(List<PrometheusMeterRegistryInitializer> initializers,
                                          Supplier<PrometheusMeterRegistry> registrySupplier) {
        this.initializers = initializers;
        this.registrySupplier = registrySupplier;
    }

    @Override
    public void init() {
        PrometheusMeterRegistry meterRegistry = registrySupplier.get();
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
        this.koraVersionMetric = Gauge.builder("kora.up", 1, i -> i)
            .tag("version", KORA_VERSION)
            .register(this.registry);
    }

    @Override
    public void release() {
        var r = this.registry;
        var gcMetrics = this.gcMetrics;
        var koraVersion = this.koraVersionMetric;
        try {
            if (gcMetrics != null) {
                gcMetrics.close();
            }
        } finally {
            try {
                if (koraVersion != null) {
                    koraVersion.close();
                }
            } finally {
                if (r != null) {
                    this.registry = null;
                    Metrics.removeRegistry(r);
                    r.close();
                }
            }
        }
    }

    @Override
    public PrometheusMeterRegistry value() {
        return this.registry;
    }

    private static String readVersion() {
        try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/kora/version/common")) {
            if (is == null) {
                return "UNKNOWN";
            }
            var bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }
}
