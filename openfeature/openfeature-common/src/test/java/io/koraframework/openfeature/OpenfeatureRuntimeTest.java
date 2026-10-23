package io.koraframework.openfeature;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.openfeature.context.*;
import io.koraframework.openfeature.mapper.OpenfeatureFlagMapper;
import io.koraframework.openfeature.mapper.OpenfeatureValueJsonReader;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetryConfig;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;
import io.koraframework.openfeature.telemetry.impl.NoopOpenfeatureObservation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenfeatureRuntimeTest {
    private final OpenFeatureAPI api = OpenFeatureAPI.createIsolated();
    private final OpenfeatureModule module = new OpenfeatureModule() {};

    @AfterEach
    void close() { api.shutdown(); }

    static OpenfeatureTelemetryConfig telemetry(boolean metrics, boolean tracing) {
        return new OpenfeatureTelemetryConfig() {
            public OpenfeatureLoggingConfig logging() { return new OpenfeatureLoggingConfig() {}; }
            public OpenfeatureTracingConfig tracing() { return new OpenfeatureTracingConfig() {
                public boolean enabled() { return tracing; }
            }; }
            public OpenfeatureMetricsConfig metrics() { return new OpenfeatureMetricsConfig() {
                public boolean enabled() { return metrics; }
            }; }
        };
    }

    static OpenfeatureConfig config(boolean async, String domain) {
        return new OpenfeatureConfig() {
            public boolean initializeAsync() { return async; }
            public String domain() { return domain; }
            public OpenfeatureTelemetryConfig telemetry() { return OpenfeatureRuntimeTest.telemetry(false, false); }
        };
    }

    static <T> Flag<T> flag(T value) {
        return Flag.<T>builder().variant("value", value).defaultVariant("value").build();
    }

    @Test
    void missingMalformedAndWrongTypeFlagsKeepOriginalDefaults() {
        api.setProviderAndWait(new InMemoryProvider(Map.of(
            "invalid", flag("garbage"), "wrong", flag(123), "valid", flag("PT2S"))));
        var mapper = module.openfeatureMapperDuration();
        var fallback = Duration.ofSeconds(3);
        for (var key : List.of("missing", "invalid", "wrong")) {
            assertThat(mapper.map(api.getClient(), key, fallback, new ImmutableContext())).isSameAs(fallback);
        }
        assertThat(mapper.map(api.getClient(), "valid", fallback, new ImmutableContext())).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void longFlagsKeepPrecisionAndFloatOverflowKeepsDefault() {
        api.setProviderAndWait(new InMemoryProvider(Map.of(
            "long", flag(Long.MAX_VALUE), "float", flag(Double.MAX_VALUE))));
        assertThat(module.openfeatureMapperLong().map(api.getClient(), "long", 0L, new ImmutableContext())).isEqualTo(Long.MAX_VALUE);
        assertThat(module.openfeatureMapperLong().map(api.getClient(), "missing", Long.MAX_VALUE, new ImmutableContext())).isEqualTo(Long.MAX_VALUE);
        assertThat(module.openfeatureMapperFloat().map(api.getClient(), "float", 2f, new ImmutableContext())).isEqualTo(2f);
    }

    private enum Mode {
        ON;
        @Override public String toString() { return "display"; }
    }

    @Test
    void enumUsesNameAndKeepsDefaultForUnknownValue() {
        api.setProviderAndWait(new InMemoryProvider(Map.of("mode", flag("ON"), "invalid", flag("missing"))));
        var mapper = module.openfeatureMapperEnum(TypeRef.of(Mode.class));
        assertThat(mapper.map(api.getClient(), "mode", Mode.ON, new ImmutableContext())).isEqualTo(Mode.ON);
        assertThat(mapper.map(api.getClient(), "invalid", Mode.ON, new ImmutableContext())).isEqualTo(Mode.ON);
    }

    @Test
    void objectMapperReadsStructuredFlagsAndDoesNotParseDefaults() {
        var value = new OpenfeatureValueJsonReader().read("{\"limit\":42}");
        api.setProviderAndWait(new InMemoryProvider(Map.of("object", flag(value))));
        OpenfeatureFlagMapper<Long> mapper = module.openfeatureMapperJson(parser -> {
            parser.nextToken();
            parser.nextToken();
            return parser.getLongValue();
        });
        assertThat(mapper.map(api.getClient(), "object", 11L, new ImmutableContext())).isEqualTo(42L);
        assertThat(mapper.map(api.getClient(), "missing", 11L, new ImmutableContext())).isEqualTo(11L);
        OpenfeatureFlagMapper<Long> invalid = module.openfeatureMapperJson(parser -> { throw new IllegalArgumentException("invalid"); });
        assertThat(invalid.map(api.getClient(), "object", 11L, new ImmutableContext())).isEqualTo(11L);
    }

    @Test
    void directObjectMapperAvoidsParsingMissingAndMalformedFlags() {
        var attempts = new AtomicInteger();
        var value = new Value(new ImmutableStructure(Map.of("limit", new Value(Long.MAX_VALUE))));
        api.setProviderAndWait(new InMemoryProvider(Map.of("object", flag(value), "invalid", flag(new Value(true)))));
        OpenfeatureFlagMapper<Long> mapper = OpenfeatureFlagMapper.fromObject(object -> {
            attempts.incrementAndGet();
            return object.asStructure().getValue("limit").asLong();
        });
        assertThat(mapper.map(api.getClient(), "missing", 11L, new ImmutableContext())).isEqualTo(11L);
        assertThat(attempts).hasValue(0);
        assertThat(mapper.map(api.getClient(), "object", 11L, new ImmutableContext())).isEqualTo(Long.MAX_VALUE);
        assertThat(mapper.map(api.getClient(), "invalid", 11L, new ImmutableContext())).isEqualTo(11L);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void graphsHaveIndependentProviderStateAndRelease() {
        var shutdowns = new AtomicInteger();
        var first = new OpenfeatureWrapper(new InMemoryProvider(Map.of("flag", flag(true))) {
            @Override public void shutdown() { shutdowns.incrementAndGet(); super.shutdown(); }
        }, config(false, null), List.of(), List.of());
        var second = new OpenfeatureWrapper(new InMemoryProvider(Map.of("flag", flag(false))),
            config(false, null), List.of(), List.of());
        try {
            first.init();
            first.init();
            second.init();
            assertThat(first.value()).isNotSameAs(OpenFeatureAPI.getInstance());
            assertThat(first.value().getHooks()).isEmpty();
            assertThat(first.value().getClient().getBooleanValue("flag", false)).isTrue();
            assertThat(second.value().getClient().getBooleanValue("flag", true)).isFalse();
            first.release();
            assertThat(shutdowns).hasValue(1);
            assertThat(second.value().getClient().getBooleanValue("flag", true)).isFalse();
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void acceptsPlainFeatureProviderAndCustomizesBeforeInitialization() {
        var provider = mock(FeatureProvider.class, CALLS_REAL_METHODS);
        when(provider.getMetadata()).thenReturn(() -> "plain");
        var configured = new AtomicInteger();
        var lifecycle = new OpenfeatureWrapper(provider, config(false, null),
            List.of(a -> { configured.incrementAndGet(); a.setEvaluationContext(new ImmutableContext("application")); return a; }), List.of());
        try {
            lifecycle.init();
            verify(provider).initialize(argThat(context -> "application".equals(context.getTargetingKey())));
            assertThat(configured).hasValue(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lifecycle.release();
        }
        verify(provider).shutdown();
    }

    @Test
    void apiConfigurersChainReturnedInstancesAndKeepHooksEventsAndRequestContext() throws Exception {
        var replacement = OpenFeatureAPI.createIsolated();
        var abandonedShutdowns = new AtomicInteger();
        var ready = new CountDownLatch(1);
        var telemetry = mock(OpenfeatureTelemetry.class);
        when(telemetry.observe(any())).thenReturn(NoopOpenfeatureObservation.INSTANCE);
        var wrapper = new OpenfeatureWrapper(new InMemoryProvider(Map.of("flag", flag(true))), config(false, null),
            List.of(original -> replacement, configured -> {
                assertThat(configured).isSameAs(replacement);
                configured.setEvaluationContext(new ImmutableContext("global"));
                return configured;
            }), List.of(new OpenfeatureEventListener() {
                @Override public void onReady(EventDetails event) { ready.countDown(); }
            }), telemetry);
        wrapper.value().setProviderAndWait(new InMemoryProvider(Map.of()) {
            @Override public void shutdown() { abandonedShutdowns.incrementAndGet(); super.shutdown(); }
        });
        try {
            wrapper.init();
            assertThat(wrapper.value()).isSameAs(replacement);
            assertThat(abandonedShutdowns).hasValue(1);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(replacement.getTransactionContextPropagator()).isInstanceOf(OpenfeatureTransactionContextPropagator.class);
            assertThat(OpenfeatureContext.call(new ImmutableContext("request"), () ->
                replacement.getClient().getBooleanValue("flag", false))).isTrue();
            verify(telemetry).observe(argThat(evaluation -> "request".equals(evaluation.getCtx().getTargetingKey())));
        } finally {
            wrapper.release();
        }
    }

    @Test
    void replacementApiKeepsApplicationTransactionPropagator() {
        var replacement = OpenFeatureAPI.createIsolated();
        var propagator = mock(TransactionContextPropagator.class);
        replacement.setTransactionContextPropagator(propagator);
        var wrapper = new OpenfeatureWrapper(new InMemoryProvider(Map.of()), config(false, null),
            List.of(original -> replacement), List.of());
        try {
            wrapper.init();
            assertThat(wrapper.value().getTransactionContextPropagator()).isSameAs(propagator);
        } finally {
            wrapper.release();
        }
    }

    @Test
    void nullApiConfigurerResultFailsStartupAndCleansProviders() {
        var shutdowns = new AtomicInteger();
        var wrapper = new OpenfeatureWrapper(new InMemoryProvider(Map.of()), config(false, null),
            List.of(original -> {
                original.setProviderAndWait(new InMemoryProvider(Map.of()) {
                    @Override public void shutdown() { shutdowns.incrementAndGet(); super.shutdown(); }
                });
                return null;
            }), List.of());
        try {
            assertThatThrownBy(wrapper::init).isInstanceOf(NullPointerException.class).hasMessage("API configurer returned null");
            assertThat(shutdowns).hasValue(1);
        } finally {
            wrapper.release();
        }
    }

    @Test
    void clientConfigurerUsesContextSnapshotAndReturnedClient() {
        var factory = new OpenfeatureFactoryModule("custom.path");
        var replacement = api.getClient("replacement");
        var observed = new AtomicReference<EvaluationContext>();
        var context = new ImmutableContext("configured-client");
        var configured = factory.openfeatureClient(api, config(false, "orders"), context, client -> {
            observed.set(client.getEvaluationContext());
            return replacement;
        });
        assertThat(configured).isSameAs(replacement);
        assertThat(observed.get()).isNotSameAs(context);
        assertThat(observed.get().getTargetingKey()).isEqualTo("configured-client");
        assertThatThrownBy(() -> factory.openfeatureClient(api, config(false, null), null, client -> null))
            .isInstanceOf(NullPointerException.class).hasMessage("Client configurer returned null");
    }

    @Test
    void configuredDomainBindsDefaultClient() {
        var lifecycle = new OpenfeatureWrapper(new InMemoryProvider(Map.of("flag", flag(true))),
            config(false, "orders"), List.of(), List.of());
        try {
            lifecycle.init();
            assertThat(module.openfeatureFactory().openfeatureClient(lifecycle.value(), config(false, "orders"), null, null)
                .getBooleanValue("flag", false)).isTrue();
            assertThat(lifecycle.value().getClient().getBooleanValue("flag", false)).isFalse();
        } finally {
            lifecycle.release();
        }
    }

    @Test
    void forwardsAllProviderEvents() throws Exception {
        var provider = new InMemoryProvider(Map.of());
        var ready = new CountDownLatch(1);
        var changed = new CountDownLatch(1);
        var stale = new CountDownLatch(1);
        var error = new CountDownLatch(1);
        var listener = new OpenfeatureEventListener() {
            public void onReady(EventDetails d) { ready.countDown(); }
            public void onConfigurationChanged(EventDetails d) { changed.countDown(); }
            public void onStale(EventDetails d) { stale.countDown(); }
            public void onError(EventDetails d) { error.countDown(); }
        };
        var lifecycle = new OpenfeatureWrapper(provider, config(false, null), List.of(), List.of(listener));
        try {
            lifecycle.init();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            provider.emitProviderConfigurationChanged(ProviderEventDetails.builder().build());
            provider.emitProviderStale(ProviderEventDetails.builder().build());
            provider.emitProviderError(ProviderEventDetails.builder().errorCode(ErrorCode.GENERAL).build());
            assertThat(changed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stale.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(error.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            lifecycle.release();
        }
    }

    @Test
    void asyncInitializationReturnsDefaultsUntilReady() throws Exception {
        var initialized = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var ready = new CountDownLatch(1);
        var provider = new InMemoryProvider(Map.of("flag", flag(true))) {
            @Override public void initialize(EvaluationContext context) throws Exception {
                initialized.countDown();
                if (!proceed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("initialization timeout");
                super.initialize(context);
            }
        };
        var lifecycle = new OpenfeatureWrapper(provider, config(true, null), List.of(), List.of(new OpenfeatureEventListener() {
            public void onReady(EventDetails details) { ready.countDown(); }
        }));
        try {
            lifecycle.init();
            assertThat(initialized.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycle.value().getClient().getBooleanValue("flag", false)).isFalse();
            proceed.countDown();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycle.value().getClient().getBooleanValue("flag", false)).isTrue();
        } finally {
            proceed.countDown();
            lifecycle.release();
        }
    }

    @Test
    void initializationFailureReleasesRegisteredProviders() {
        var lifecycle = new OpenfeatureWrapper(new InMemoryProvider(Map.of()) {
            @Override public void initialize(EvaluationContext context) { throw new IllegalStateException("offline"); }
        }, config(false, null), List.of(), List.of());
        assertThatThrownBy(lifecycle::init).isInstanceOf(RuntimeException.class);
        lifecycle.release();
    }

    @Test
    void requestContextMergesWithInvocationAndLeavesScopeClean() {
        var captured = new AtomicReference<EvaluationContext>();
        api.setTransactionContextPropagator(new OpenfeatureTransactionContextPropagator());
        api.setEvaluationContext(new ImmutableContext(Map.of("global", new Value(true), "shared", new Value("global"))));
        api.setProviderAndWait(new InMemoryProvider(Map.of("flag", Flag.<Boolean>builder()
            .variant("value", true).defaultVariant("value").contextEvaluator((flag, context) -> {
                captured.set(context);
                return true;
            }).build())));
        var request = new MutableContext("request").add("shared", "request");
        OpenfeatureContext.run(request, () -> {
            request.add("shared", "mutated");
            api.getClient().getBooleanValue("flag", false, new ImmutableContext(Map.of("shared", new Value("invocation"))));
            assertThat(captured.get().getTargetingKey()).isEqualTo("request");
            assertThat(captured.get().getValue("global").asBoolean()).isTrue();
            assertThat(captured.get().getValue("shared").asString()).isEqualTo("invocation");
            OpenfeatureContext.run(new ImmutableContext("nested"), () ->
                assertThat(OpenfeatureContext.current().getTargetingKey()).isEqualTo("nested"));
            assertThat(OpenfeatureContext.current().getTargetingKey()).isEqualTo("request");
        });
        assertThat(OpenfeatureContext.current()).isNull();
        assertThatThrownBy(() -> OpenfeatureContext.run(new ImmutableContext(), () -> { throw new IllegalStateException(); }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(OpenfeatureContext.current()).isNull();
    }

    @Test
    void requestContextIsolatedAcrossConcurrentVirtualThreads() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                executor.submit(() -> OpenfeatureContext.call(new ImmutableContext("user-" + i),
                    () -> OpenfeatureContext.current().getTargetingKey()))).toList();
            for (int i = 0; i < futures.size(); i++) {
                assertThat(futures.get(i).get(5, TimeUnit.SECONDS)).isEqualTo("user-" + i);
            }
        }
        assertThat(OpenfeatureContext.current()).isNull();
    }

}
