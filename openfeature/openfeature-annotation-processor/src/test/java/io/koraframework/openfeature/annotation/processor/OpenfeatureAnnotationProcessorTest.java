package io.koraframework.openfeature.annotation.processor;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import io.koraframework.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.openfeature.context.Contextable;
import io.koraframework.openfeature.OpenfeatureFlagRegistrar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class OpenfeatureAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.openfeature.*;
            import io.koraframework.openfeature.context.*;
            import io.koraframework.openfeature.mapper.*;
            import io.koraframework.openfeature.annotation.*;
            import dev.openfeature.sdk.*;
            """;
    }

    private void compileFlags(String... sources) {
        compile(List.of(new OpenfeatureAnnotationProcessor(), new ConfigParserAnnotationProcessor(),
            new ConfigSourceAnnotationProcessor()), sources).assertSuccess();
    }

    private Object config(Map<String, Object> values) {
        var type = compileResult.loadClass("$Flags_Config");
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, arguments) -> values.get(method.getName()));
    }

    @Test
    void inheritedGenericFlagsContextAndLazyDefaultsWork() {
        compileFlags("""
            public interface Parent<T> {
                default T inherited() { return (T) "parent"; }
            }
            """, """
            @OpenfeatureSource("flags")
            public interface Flags extends Parent<String>, Contextable<Flags> {
                default boolean enabled() { throw new AssertionError("must not evaluate configured default"); }
                default long big() { return Long.MAX_VALUE; }
                static String helper() { return "helper"; }
                default void helper(String value) {}
            }
            """);
        var api = OpenFeatureAPI.createIsolated();
        try {
            api.setProviderAndWait(new InMemoryProvider(Map.of()));
            var instance = newObject("$Flags_Impl", api.getClient(), config(Map.of("enabled", false)), new ImmutableContext());
            assertThat(invoke(instance, "enabled")).isEqualTo(false);
            assertThat(invoke(instance, "inherited")).isEqualTo("parent");
            assertThat(invoke(instance, "big")).isEqualTo(Long.MAX_VALUE);
            @SuppressWarnings("unchecked")
            var contextual = ((Contextable<Object>) instance).withContext(new ImmutableContext("user"));
            assertThat(contextual).isNotSameAs(instance);
            assertThat(invoke(contextual, "inherited")).isEqualTo("parent");
        } finally {
            api.shutdown();
        }
    }

    @Test
    void customTaggedMapperOverridesNativeTypeAndRegistration() throws Exception {
        compileFlags("""
            public final class CustomMapper implements OpenfeatureFlagMapper<String> {
                public String map(Features features, String key, String fallback, EvaluationContext context) {
                    return context.getTargetingKey() + ":" + fallback;
                }
            }
            """, """
            @OpenfeatureSource("flags")
            public interface Flags extends Contextable<Flags> {
                @Mapping(CustomMapper.class)
                @Tag(CustomMapper.class)
                @OpenfeatureType(FlagValueType.BOOLEAN)
                default String mapped() { return "fallback"; }
            }
            """);
        var constructor = compileResult.loadClass("$Flags_Impl").getConstructors()[0];
        var tag = constructor.getParameters()[3].getAnnotation(io.koraframework.common.annotation.Tag.class);
        assertThat(tag.value()).isEqualTo(compileResult.loadClass("CustomMapper"));
        var api = OpenFeatureAPI.createIsolated();
        try {
            var instance = newObject("$Flags_Impl", api.getClient(), config(Map.of()), new ImmutableContext("user"), newObject("CustomMapper"));
            assertThat(invoke(instance, "mapped")).isEqualTo("user:fallback");
            var module = compileResult.loadClass("$Flags_Module");
            var implementation = Proxy.newProxyInstance(module.getClassLoader(), new Class<?>[] {module}, InvocationHandler::invokeDefault);
            var registrar = (OpenfeatureFlagRegistrar) module.getMethod("flagsFlagRegistration").invoke(implementation);
            assertThat(registrar.flags()).containsEntry("flags.mapped", FlagValueType.BOOLEAN);
        } finally {
            api.shutdown();
        }
    }

    @Test
    void generatedSourcesBuildAndRunInKoraGraph() throws Exception {
        compile(List.of(new OpenfeatureAnnotationProcessor(), new ConfigParserAnnotationProcessor(),
            new ConfigSourceAnnotationProcessor(), new KoraAppProcessor()), """
            @OpenfeatureSource("flags")
            public interface Flags { default boolean enabled() { return false; } }
            """, """
            @OpenfeatureSource("other")
            public interface Other { default long limit() { return Long.MAX_VALUE; } }
            """, """
            @KoraApp
            public interface App extends OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of());
                }
                default FeatureProvider provider() {
                    return new dev.openfeature.sdk.providers.memory.InMemoryProvider(java.util.Map.of(
                        "flags.enabled", dev.openfeature.sdk.providers.memory.Flag.<Boolean>builder()
                            .variant("on", true).defaultVariant("on").build()));
                }
                @Root
                default String root(Flags flags, Other other, io.koraframework.application.graph.All<OpenfeatureFlagRegistrar> registrations) {
                    int count = 0;
                    for (var registration : registrations) { count += registration.flags().size(); }
                    return flags.enabled() + ":" + other.limit() + ":" + count;
                }
            }
            """).assertSuccess();
        @SuppressWarnings("unchecked")
        var supplier = (Supplier<ApplicationGraphDraw>) compileResult.loadClass("AppGraph").getConstructor().newInstance();
        var draw = supplier.get();
        var graph = draw.init();
        try {
            var values = draw.getNodes().stream().map(graph::get).toList();
            assertThat(values).anyMatch(value -> ("true:" + Long.MAX_VALUE + ":2").equals(value));
        } finally {
            graph.release();
        }
    }

    @Test
    void taggedFactoriesKeepProvidersConfigurersAndRegistrarsIndependent() throws Exception {
        compile(List.of(new OpenfeatureAnnotationProcessor(), new ConfigParserAnnotationProcessor(),
            new ConfigSourceAnnotationProcessor(), new KoraAppProcessor()),
            "public class First {}", "public class Second {}", """
            public class State {
                public String path;
                public final java.util.concurrent.atomic.AtomicInteger evaluations = new java.util.concurrent.atomic.AtomicInteger();
            }
            """, """
            @OpenfeatureSource(value = "flags.first", clientTag = First.class)
            public interface FirstFlags extends Contextable<FirstFlags> {
                @OpenfeatureKey("shared") default boolean enabled() { return false; }
            }
            """, """
            @OpenfeatureSource(value = "flags.second", clientTag = Second.class)
            public interface SecondFlags {
                @OpenfeatureKey("shared") default boolean enabled() { return true; }
            }
            """, """
            @KoraApp
            public interface App extends OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                @FactoryModule @Tag(First.class)
                default OpenfeatureFactoryModule firstFactory() { return new OpenfeatureFactoryModule("providers.first"); }
                @FactoryModule @Tag(Second.class)
                default OpenfeatureFactoryModule secondFactory() { return new OpenfeatureFactoryModule("providers.second"); }
                default State state() { return new State(); }
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of(
                        "providers", java.util.Map.of("first", java.util.Map.of("domain", "same-domain"),
                                                     "second", java.util.Map.of("domain", "same-domain"))));
                }
                @Tag(First.class) default FeatureProvider firstProvider() { return provider(true); }
                @Tag(Second.class) default FeatureProvider secondProvider() { return provider(false); }
                private FeatureProvider provider(boolean enabled) {
                    return new dev.openfeature.sdk.providers.memory.InMemoryProvider(java.util.Map.of(
                        "shared", dev.openfeature.sdk.providers.memory.Flag.<Boolean>builder()
                            .variant("value", enabled).defaultVariant("value").build()));
                }
                @Tag(First.class)
                default io.koraframework.common.Configurer<OpenFeatureAPI> firstConfigurer() {
                    return api -> { api.setEvaluationContext(new ImmutableContext("api-first")); return api; };
                }
                @Tag(First.class) default EvaluationContext firstContext() { return new ImmutableContext("client-first"); }
                @Tag(Second.class)
                default io.koraframework.common.Configurer<Client> secondConfigurer() {
                    return client -> { client.setEvaluationContext(new ImmutableContext("client-second")); return client; };
                }
                @Tag(First.class)
                default io.koraframework.openfeature.telemetry.OpenfeatureTelemetryFactory firstTelemetry(State state) {
                    return (path, name, config) -> {
                        state.path = path;
                        return evaluation -> {
                            state.evaluations.incrementAndGet();
                            return io.koraframework.openfeature.telemetry.impl.NoopOpenfeatureObservation.INSTANCE;
                        };
                    };
                }
                @Root
                default String root(FirstFlags first, SecondFlags second, State state,
                    @Tag(First.class) Client firstClient, @Tag(Second.class) Client secondClient,
                    @Tag(First.class) OpenFeatureAPI firstApi, @Tag(Second.class) OpenFeatureAPI secondApi,
                    @Tag(First.class) io.koraframework.application.graph.All<OpenfeatureFlagRegistrar> firstRegistrars,
                    @Tag(Second.class) io.koraframework.application.graph.All<OpenfeatureFlagRegistrar> secondRegistrars) {
                    if (firstApi == secondApi) throw new AssertionError("shared API");
                    var result = first.enabled() + ":" + second.enabled() + ":"
                        + first.withContext(new ImmutableContext("invocation")).enabled() + ":"
                        + firstClient.getEvaluationContext().getTargetingKey() + ":"
                        + secondClient.getEvaluationContext().getTargetingKey() + ":"
                        + firstApi.getEvaluationContext().getTargetingKey() + ":"
                        + state.path + ":" + state.evaluations.get();
                    int firstCount = 0, secondCount = 0;
                    for (var registrar : firstRegistrars) firstCount += registrar.flags().size();
                    for (var registrar : secondRegistrars) secondCount += registrar.flags().size();
                    firstApi.shutdown();
                    return result + ":" + firstCount + ":" + secondCount + ":"
                        + secondClient.getBooleanValue("shared", true);
                }
            }
            """).assertSuccess();
        var constructor = compileResult.loadClass("$FirstFlags_Impl").getConstructors()[0];
        assertThat(constructor.getParameters()[0].getAnnotation(io.koraframework.common.annotation.Tag.class).value())
            .isEqualTo(compileResult.loadClass("First"));
        @SuppressWarnings("unchecked")
        var supplier = (Supplier<ApplicationGraphDraw>) compileResult.loadClass("AppGraph").getConstructor().newInstance();
        var draw = supplier.get();
        var graph = draw.init();
        try {
            assertThat(draw.getNodes().stream().map(graph::get).toList())
                .anyMatch(value -> "true:false:true:client-first:client-second:api-first:providers.first:2:1:1:false".equals(value));
        } finally {
            graph.release();
        }
    }

    @Test
    void nestedSourcesWithSameNameHaveDistinctFactories() {
        compileFlags("""
            public interface First {
                @OpenfeatureSource("a") interface Flags { default boolean enabled() { return false; } }
            }
            """, """
            public interface Second {
                @OpenfeatureSource("b") interface Flags { default boolean enabled() { return false; } }
            }
            """, """
            public interface Both extends $First_Flags_Module, $Second_Flags_Module {}
            """);
        assertThat(compileResult.loadClass("Both").getMethods()).anySatisfy(method ->
            assertThat(method.getName()).isEqualTo("first_FlagsFlagRegistration"));
    }

    @Test
    void genericCustomMappersAreParameterized() {
        compileFlags("""
            public final class GenericMapper<T> implements OpenfeatureFlagMapper<T> {
                public T map(Features features, String key, T fallback, EvaluationContext context) { return fallback; }
            }
            """, """
            @OpenfeatureSource("flags") public interface Flags extends Contextable<Flags> {
                @Mapping(GenericMapper.class) default boolean value() { return true; }
            }
            """);
        assertThat(compileResult.loadClass("$Flags_Impl").getConstructors()[0].getGenericParameterTypes()[3].getTypeName())
            .endsWith("GenericMapper<java.lang.Boolean>");
    }

    @Test
    void explicitProviderKeyKeepsConfigurationMethodName() throws Exception {
        compileFlags("""
            @OpenfeatureSource("flags") public interface Flags {
                @OpenfeatureKey("vendor-checkout-v2") default boolean enabled() { return false; }
            }
            """);
        var api = OpenFeatureAPI.createIsolated();
        try {
            api.setProviderAndWait(new InMemoryProvider(Map.of("vendor-checkout-v2",
                Flag.<Boolean>builder().variant("on", true).defaultVariant("on").build())));
            var instance = newObject("$Flags_Impl", api.getClient(), config(Map.of()), new ImmutableContext());
            assertThat(invoke(instance, "enabled")).isEqualTo(true);
            assertThat(compileResult.loadClass("$Flags_Config").getMethod("enabled")).isNotNull();
            var module = compileResult.loadClass("$Flags_Module");
            var implementation = Proxy.newProxyInstance(module.getClassLoader(), new Class<?>[] {module}, InvocationHandler::invokeDefault);
            var registrar = (OpenfeatureFlagRegistrar) module.getMethod("flagsFlagRegistration").invoke(implementation);
            assertThat(registrar.flags()).containsOnlyKeys("vendor-checkout-v2");
        } finally {
            api.shutdown();
        }
    }

    @Test
    void jsonAndConvertedFlagsResolveThroughKoraGraph() throws Exception {
        compile(List.of(new OpenfeatureAnnotationProcessor(), new ConfigParserAnnotationProcessor(),
            new ConfigSourceAnnotationProcessor(), new JsonAnnotationProcessor(), new KoraAppProcessor()), """
            @io.koraframework.config.common.annotation.ConfigMapper
            @io.koraframework.json.common.annotation.Json
            public record Settings(int limit) {}
            """, """
            public enum Mode { ON, OFF }
            """, """
            @OpenfeatureSource("flags") public interface Flags {
                default java.time.Duration timeout() { return java.time.Duration.ofSeconds(2); }
                default Mode mode() { return Mode.OFF; }
                @io.koraframework.json.common.annotation.Json
                default Settings settings() { return new Settings(5); }
            }
            """, """
            @KoraApp
            public interface App extends OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule,
                io.koraframework.json.common.JsonModule {
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of());
                }
                default FeatureProvider provider() {
                    return new dev.openfeature.sdk.providers.memory.InMemoryProvider(java.util.Map.of(
                        "flags.timeout", dev.openfeature.sdk.providers.memory.Flag.<String>builder()
                            .variant("value", "PT4S").defaultVariant("value").build(),
                        "flags.mode", dev.openfeature.sdk.providers.memory.Flag.<String>builder()
                            .variant("value", "ON").defaultVariant("value").build(),
                        "flags.settings", dev.openfeature.sdk.providers.memory.Flag.<Value>builder()
                            .variant("value", new Value(new ImmutableStructure(java.util.Map.of("limit", new Value(42)))))
                            .defaultVariant("value").build()));
                }
                @Root default String root(Flags flags) {
                    return flags.timeout() + ":" + flags.mode() + ":" + flags.settings().limit();
                }
            }
            """).assertSuccess();
        @SuppressWarnings("unchecked")
        var supplier = (Supplier<ApplicationGraphDraw>) compileResult.loadClass("AppGraph").getConstructor().newInstance();
        var draw = supplier.get();
        var graph = draw.init();
        try {
            assertThat(draw.getNodes().stream().map(graph::get).toList())
                .anyMatch(value -> "PT4S:ON:42".equals(value));
        } finally {
            graph.release();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "class Flags {}",
        "interface Flags<T> { T value(); }",
        "interface Flags { int value(String key); }",
        "interface Flags { void value(); }",
        "interface Flags { <T> T value(); }",
        "interface Flags { @Nullable String value(); }",
        "interface Flags { @OpenfeatureType(FlagValueType.BOOLEAN) String value(); }",
        "interface Flags { @OpenfeatureKey(\"\") boolean value(); }",
        "interface Flags { @OpenfeatureKey(\"same\") boolean first(); @OpenfeatureKey(\"same\") boolean second(); }"
    })
    void invalidDeclarationsReportProcessorErrors(String declaration) {
        var result = compile(List.of(new OpenfeatureAnnotationProcessor()), "@OpenfeatureSource(\"flags\") public " + declaration);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error.getMessage(java.util.Locale.ROOT)).containsAnyOf("OpenFeature", "Openfeature", "@OpenfeatureSource"));
    }
}
