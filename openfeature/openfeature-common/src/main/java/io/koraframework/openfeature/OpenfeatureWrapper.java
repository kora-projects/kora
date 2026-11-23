package io.koraframework.openfeature;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpTransactionContextPropagator;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
import io.koraframework.openfeature.context.OpenfeatureTransactionContextPropagator;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;
import io.koraframework.openfeature.telemetry.impl.NoopOpenfeatureTelemetry;
import io.koraframework.openfeature.telemetry.impl.OpenfeatureTelemetryHook;

import java.util.Objects;

/** Owns an API per factory instance; provider initialization never touches the SDK singleton. */
public final class OpenfeatureWrapper implements Lifecycle, Wrapped<OpenFeatureAPI> {
    private final FeatureProvider provider;
    private final OpenfeatureConfig config;
    private final Iterable<Configurer<OpenFeatureAPI>> configurers;
    private final Iterable<OpenfeatureEventListener> listeners;
    private final OpenfeatureTelemetry telemetry;

    private boolean initialized;
    private volatile OpenFeatureAPI api = OpenFeatureAPI.createIsolated();

    public OpenfeatureWrapper(FeatureProvider provider, OpenfeatureConfig config,
                              Iterable<Configurer<OpenFeatureAPI>> configurers,
                              Iterable<OpenfeatureEventListener> listeners) {
        this(provider, config, configurers, listeners, NoopOpenfeatureTelemetry.INSTANCE);
    }

    public OpenfeatureWrapper(FeatureProvider provider, OpenfeatureConfig config,
                              Iterable<Configurer<OpenFeatureAPI>> configurers,
                              Iterable<OpenfeatureEventListener> listeners,
                              OpenfeatureTelemetry telemetry) {
        this.provider = provider;
        this.config = config;
        this.configurers = configurers;
        this.listeners = listeners;
        this.telemetry = telemetry;
    }

    @Override
    public OpenFeatureAPI value() {
        return api;
    }

    @Override
    public synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            api.setTransactionContextPropagator(new OpenfeatureTransactionContextPropagator());
            for (var configurer : configurers) {
                var previous = this.api;
                this.api = Objects.requireNonNull(configurer.configure(previous), "API configurer returned null");
                if (this.api != previous) {
                    if (this.api.getTransactionContextPropagator() instanceof NoOpTransactionContextPropagator) {
                        this.api.setTransactionContextPropagator(previous.getTransactionContextPropagator());
                    }
                    previous.shutdown();
                }
            }
            for (var listener : listeners) {
                api.onProviderReady(listener::onReady);
                api.onProviderError(listener::onError);
                api.onProviderStale(listener::onStale);
                api.onProviderConfigurationChanged(listener::onConfigurationChanged);
            }
            if (telemetry != NoopOpenfeatureTelemetry.INSTANCE) {
                api.addHooks(new OpenfeatureTelemetryHook(telemetry));
            }
            if (config.domain() == null) {
                if (config.initializeAsync()) {
                    api.setProvider(provider);
                } else {
                    api.setProviderAndWait(provider);
                }
            } else if (config.initializeAsync()) {
                api.setProvider(config.domain(), provider);
            } else {
                api.setProviderAndWait(config.domain(), provider);
            }
            initialized = true;
        } catch (RuntimeException | Error e) {
            try {
                api.shutdown();
            } catch (RuntimeException | Error cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    @Override
    public synchronized void release() {
        api.shutdown();
        initialized = false;
    }
}
