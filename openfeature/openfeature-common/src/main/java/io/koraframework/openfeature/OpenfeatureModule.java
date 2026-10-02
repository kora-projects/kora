package io.koraframework.openfeature;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.hooks.logging.LoggingHook;
import jakarta.annotation.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.LifecycleWrapper;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

import java.util.Objects;
import java.util.function.Consumer;

public interface OpenfeatureModule extends OpenfeatureMapperModule {

    default OpenfeatureConfig openfeatureConfig(Config config, ConfigValueExtractor<OpenfeatureConfig> extractor) {
        var value = config.get("openfeature");
        return extractor.extract(value);
    }

    default Wrapped<OpenFeatureAPI> openfeatureAPI(EventProvider eventProvider,
                                                   All<StringHook> stringHooks,
                                                   All<IntegerHook> integerHooks,
                                                   All<DoubleHook> doubleHooks,
                                                   All<BooleanHook> booleanHooks,
                                                   All<Consumer<EventDetails>> handlers,
                                                   OpenfeatureConfig config) {
        return new LifecycleWrapper<>(OpenFeatureAPI.getInstance(),
            openFeatureApi -> {
                if (Objects.requireNonNullElse(config.telemetry().logging().enabled(), false)) {
                    openFeatureApi.addHooks(new LoggingHook());
                }
                openFeatureApi.addHooks(stringHooks.toArray(new StringHook[0]));
                openFeatureApi.addHooks(integerHooks.toArray(new IntegerHook[0]));
                openFeatureApi.addHooks(doubleHooks.toArray(new DoubleHook[0]));
                openFeatureApi.addHooks(booleanHooks.toArray(new BooleanHook[0]));

                handlers.forEach(openFeatureApi::onProviderReady);

                if (config.initializeAsync()) {
                    openFeatureApi.setProvider(eventProvider);
                } else {
                    openFeatureApi.setProviderAndWait(eventProvider);
                }
            },
            OpenFeatureAPI::shutdown);
    }

    default Client openfeatureClient(OpenFeatureAPI openFeatureAPI,
                                     @Nullable EvaluationContext ctx,
                                     All<Consumer<EventDetails>> handlers) {
        var client = openFeatureAPI.getClient();
        if (ctx != null) {
            client.setEvaluationContext(ctx);
        }
        client.onProviderConfigurationChanged(eventDetails ->
            handlers.forEach(consumer -> consumer.accept(eventDetails))
        );
        return client;
    }
}
