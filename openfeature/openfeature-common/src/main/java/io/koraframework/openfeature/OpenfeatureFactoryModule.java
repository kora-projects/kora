package io.koraframework.openfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.koraframework.application.graph.All;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetryFactory;
import io.koraframework.openfeature.telemetry.impl.DefaultOpenfeatureLoggerFactory;
import io.koraframework.openfeature.telemetry.impl.DefaultOpenfeatureMetricsFactory;
import io.koraframework.openfeature.telemetry.impl.DefaultOpenfeatureTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Instance wiring; factory tags select the provider, configurers, context, events and telemetry. */
public class OpenfeatureFactoryModule {

    private final String configPath;

    public OpenfeatureFactoryModule(String configPath) {
        this.configPath = Objects.requireNonNull(configPath);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public OpenfeatureConfig openfeatureConfig(Config config, ConfigValueMapper<OpenfeatureConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public OpenfeatureWrapper openfeatureAPI(@Tag(Tag.Factory.class) FeatureProvider provider,
                                             @Tag(Tag.Factory.class) OpenfeatureConfig config,
                                             @Tag(Tag.Factory.class) All<Configurer<OpenFeatureAPI>> configurers,
                                             @Tag(Tag.Factory.class) All<OpenfeatureEventListener> listeners,
                                             @Tag(Tag.Factory.class) OpenfeatureTelemetry telemetry) {
        return new OpenfeatureWrapper(provider, config, configurers, listeners, telemetry);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public Client openfeatureClient(@Tag(Tag.Factory.class) OpenFeatureAPI api,
                                     @Tag(Tag.Factory.class) OpenfeatureConfig config,
                                     @Tag(Tag.Factory.class) @Nullable EvaluationContext context,
                                     @Tag(Tag.Factory.class) @Nullable Configurer<Client> configurer) {
        var client = config.domain() == null ? api.getClient() : api.getClient(config.domain());
        if (context != null) {
            client.setEvaluationContext(new ImmutableContext(context.getTargetingKey(), context.asMap()));
        }
        return configurer == null ? client : Objects.requireNonNull(configurer.configure(client), "Client configurer returned null");
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public OpenfeatureTelemetryFactory openfeatureTelemetryFactory(@Nullable Tracer tracer,
                                                                   @Nullable MeterRegistry meterRegistry,
                                                                   @Tag(Tag.Factory.class) @Nullable DefaultOpenfeatureLoggerFactory loggerFactory,
                                                                   @Tag(Tag.Factory.class) @Nullable DefaultOpenfeatureMetricsFactory metricsFactory) {
        return new DefaultOpenfeatureTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public OpenfeatureTelemetry openfeatureTelemetry(@Tag(Tag.Factory.class) OpenfeatureConfig config,
                                                     @Tag(Tag.Factory.class) FeatureProvider provider,
                                                     @Tag(Tag.Factory.class) OpenfeatureTelemetryFactory factory) {
        var providerClass = provider.getClass();
        var canonicalName = providerClass.getCanonicalName();
        return factory.get(this.configPath, canonicalName != null ? canonicalName : providerClass.getName(), config.telemetry());
    }
}
