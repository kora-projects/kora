package io.koraframework.openfeature.symbol.processor

import dev.openfeature.sdk.*
import dev.openfeature.sdk.providers.memory.InMemoryProvider
import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.json.ksp.JsonSymbolProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraph
import io.koraframework.openfeature.context.Contextable
import io.koraframework.openfeature.OpenfeatureFlagRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class OpenfeatureSymbolProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import io.koraframework.openfeature.*
            import io.koraframework.openfeature.context.*
            import io.koraframework.openfeature.mapper.*
        import io.koraframework.openfeature.annotation.*
        import dev.openfeature.sdk.*
    """

    private val processors = listOf(OpenfeatureSymbolProcessorProvider(),
        ConfigParserSymbolProcessorProvider(), ConfigSourceSymbolProcessorProvider())

    private fun config(values: Map<String, Any>): Any {
        val type = loadClass("$" + "Flags_Config")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> values[method.name] }
    }

    @Test
    fun inheritedGenericFlagsContextAndLazyDefaultsWork() {
        compile0(processors, emptyList(), """
            interface Parent<T> { fun inherited(): T = "parent" as T }
        """, """
            @OpenfeatureSource("flags")
            interface Flags : Parent<String>, Contextable<Flags> {
                fun enabled(): Boolean = error("must not evaluate configured default")
                fun big(): Long = Long.MAX_VALUE
                fun helper(value: String) {}
            }
        """).assertSuccess()
        val api = OpenFeatureAPI.createIsolated()
        try {
            api.setProviderAndWait(InMemoryProvider(emptyMap()))
            val instance = new("$" + "Flags_Impl", api.client, config(mapOf("enabled" to false)), ImmutableContext())
            assertThat(instance.javaClass.getMethod("enabled").invoke(instance)).isEqualTo(false)
            assertThat(instance.javaClass.getMethod("inherited").invoke(instance)).isEqualTo("parent")
            assertThat(instance.javaClass.getMethod("big").invoke(instance)).isEqualTo(Long.MAX_VALUE)
            @Suppress("UNCHECKED_CAST")
            val contextual = (instance as Contextable<Any>).withContext(ImmutableContext("user"))
            assertThat(contextual).isNotSameAs(instance)
            assertThat(contextual.javaClass.getMethod("inherited").invoke(contextual)).isEqualTo("parent")
        } finally {
            api.shutdown()
        }
    }

    @Test
    fun customTaggedMapperOverridesNativeTypeAndRegistration() {
        compile0(processors, emptyList(), """
            class CustomMapper : OpenfeatureFlagMapper<String> {
                override fun map(features: Features, key: String, fallback: String, context: EvaluationContext): String =
                    context.targetingKey + ":" + fallback
            }
        """, """
            @OpenfeatureSource("flags")
            interface Flags : Contextable<Flags> {
                @Mapping(CustomMapper::class)
                @Tag(CustomMapper::class)
                @OpenfeatureType(FlagValueType.BOOLEAN)
                fun mapped(): String = "fallback"
            }
        """).assertSuccess()
        val constructor = loadClass("$" + "Flags_Impl").constructors[0]
        val tag = constructor.parameters[3].getAnnotation(io.koraframework.common.annotation.Tag::class.java)
        assertThat(tag.value.java).isEqualTo(loadClass("CustomMapper"))
        val api = OpenFeatureAPI.createIsolated()
        try {
            val instance = new("$" + "Flags_Impl", api.client, config(emptyMap()), ImmutableContext("user"), new("CustomMapper"))
            assertThat(instance.javaClass.getMethod("mapped").invoke(instance)).isEqualTo("user:fallback")
            val module = loadClass("$" + "Flags_Module")
            val implementation = Proxy.newProxyInstance(module.classLoader, arrayOf(module),
                InvocationHandler::invokeDefault)
            val registrar = module.getMethod("flagsFlagRegistration").invoke(implementation) as OpenfeatureFlagRegistrar
            assertThat(registrar.flags()).containsEntry("flags.mapped", FlagValueType.BOOLEAN)
        } finally {
            api.shutdown()
        }
    }

    @Test
    fun generatedSourcesBuildAndRunInKoraGraph() {
        compile0(processors + KoraAppProcessorProvider(), emptyList(), """
            @OpenfeatureSource("flags")
            interface Flags { fun enabled(): Boolean = false }
        """, """
            @OpenfeatureSource("other")
            interface Other { fun limit(): Long = Long.MAX_VALUE }
        """, """
            @KoraApp
            interface App : OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                fun config(): io.koraframework.config.common.Config =
                    io.koraframework.config.common.util.ConfigMappingUtils.fromMap(emptyMap<String, Any>())
                fun provider(): FeatureProvider = dev.openfeature.sdk.providers.memory.InMemoryProvider(mapOf(
                    "flags.enabled" to dev.openfeature.sdk.providers.memory.Flag.builder<Boolean>()
                        .variant("on", true).defaultVariant("on").build()
                ))
                @Root
                fun root(flags: Flags, other: Other, registrations: io.koraframework.application.graph.All<OpenfeatureFlagRegistrar>): String =
                    flags.enabled().toString() + ":" + other.limit() + ":" + registrations.sumOf { it.flags().size }
            }
        """).assertSuccess()
        loadClass("AppGraph").toGraph().use { graph ->
            assertThat(graph.findByType(String::class.java)).isEqualTo("true:" + Long.MAX_VALUE + ":2")
        }
    }

    @Test
    fun taggedFactoriesKeepProvidersConfigurersAndRegistrarsIndependent() {
        compile0(processors + KoraAppProcessorProvider(), emptyList(),
            "class First", "class Second", """
            class State {
                var path: String = ""
                val evaluations = java.util.concurrent.atomic.AtomicInteger()
            }
        """, """
            @OpenfeatureSource(value = "flags.first", clientTag = First::class)
            interface FirstFlags : Contextable<FirstFlags> {
                @OpenfeatureKey("shared") fun enabled(): Boolean = false
            }
        """, """
            @OpenfeatureSource(value = "flags.second", clientTag = Second::class)
            interface SecondFlags {
                @OpenfeatureKey("shared") fun enabled(): Boolean = true
            }
        """, """
            @KoraApp
            interface App : OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule {
                @FactoryModule @Tag(First::class)
                fun firstFactory(): OpenfeatureFactoryModule = OpenfeatureFactoryModule("providers.first")
                @FactoryModule @Tag(Second::class)
                fun secondFactory(): OpenfeatureFactoryModule = OpenfeatureFactoryModule("providers.second")
                fun state(): State = State()
                fun config(): io.koraframework.config.common.Config =
                    io.koraframework.config.common.util.ConfigMappingUtils.fromMap(mapOf(
                        "providers" to mapOf("first" to mapOf("domain" to "same-domain"),
                                             "second" to mapOf("domain" to "same-domain"))))
                @Tag(First::class) fun firstProvider(): FeatureProvider = provider(true)
                @Tag(Second::class) fun secondProvider(): FeatureProvider = provider(false)
                private fun provider(enabled: Boolean): FeatureProvider =
                    dev.openfeature.sdk.providers.memory.InMemoryProvider(mapOf(
                        "shared" to dev.openfeature.sdk.providers.memory.Flag.builder<Boolean>()
                            .variant("value", enabled).defaultVariant("value").build()))
                @Tag(First::class)
                fun firstConfigurer(): io.koraframework.common.Configurer<OpenFeatureAPI> =
                    io.koraframework.common.Configurer { api ->
                        api.evaluationContext = ImmutableContext("api-first")
                        api
                    }
                @Tag(First::class) fun firstContext(): EvaluationContext = ImmutableContext("client-first")
                @Tag(Second::class)
                fun secondConfigurer(): io.koraframework.common.Configurer<Client> =
                    io.koraframework.common.Configurer { client ->
                        client.evaluationContext = ImmutableContext("client-second")
                        client
                    }
                @Tag(First::class)
                fun firstTelemetry(state: State): io.koraframework.openfeature.telemetry.OpenfeatureTelemetryFactory =
                    io.koraframework.openfeature.telemetry.OpenfeatureTelemetryFactory { path, name, config ->
                        state.path = path
                        io.koraframework.openfeature.telemetry.OpenfeatureTelemetry { evaluation ->
                            state.evaluations.incrementAndGet()
                            io.koraframework.openfeature.telemetry.impl.NoopOpenfeatureObservation.INSTANCE
                        }
                    }
                @Root
                fun root(first: FirstFlags, second: SecondFlags, state: State,
                    @Tag(First::class) firstClient: Client, @Tag(Second::class) secondClient: Client,
                    @Tag(First::class) firstApi: OpenFeatureAPI, @Tag(Second::class) secondApi: OpenFeatureAPI,
                    @Tag(First::class) firstRegistrars: io.koraframework.application.graph.All<OpenfeatureFlagRegistrar>,
                    @Tag(Second::class) secondRegistrars: io.koraframework.application.graph.All<OpenfeatureFlagRegistrar>): String {
                    check(firstApi !== secondApi)
                    val result = first.enabled().toString() + ":" + second.enabled() + ":" +
                        first.withContext(ImmutableContext("invocation")).enabled() + ":" +
                        firstClient.evaluationContext.targetingKey + ":" + secondClient.evaluationContext.targetingKey + ":" +
                        firstApi.evaluationContext.targetingKey + ":" + state.path + ":" + state.evaluations.get()
                    val firstCount = firstRegistrars.sumOf { it.flags().size }
                    val secondCount = secondRegistrars.sumOf { it.flags().size }
                    firstApi.shutdown()
                    return result + ":" + firstCount + ":" + secondCount + ":" + secondClient.getBooleanValue("shared", true)
                }
            }
        """).assertSuccess()
        val constructor = loadClass("$" + "FirstFlags_Impl").constructors[0]
        val tag = constructor.parameters[0].getAnnotation(io.koraframework.common.annotation.Tag::class.java)
        assertThat(tag.value.java).isEqualTo(loadClass("First"))
        loadClass("AppGraph").toGraph().use { graph ->
            assertThat(graph.findByType(String::class.java))
                .isEqualTo("true:false:true:client-first:client-second:api-first:providers.first:2:1:1:false")
        }
    }

    @Test
    fun nestedSourcesWithSameNameHaveDistinctFactories() {
        compile0(processors, emptyList(), """
            interface First {
                @OpenfeatureSource("a") interface Flags { fun enabled(): Boolean = false }
            }
        """, """
            interface Second {
                @OpenfeatureSource("b") interface Flags { fun enabled(): Boolean = false }
            }
        """, "interface Both : `" + "$" + "First_Flags_Module`, `" + "$" + "Second_Flags_Module`").assertSuccess()
        assertThat(loadClass("Both").methods.map { it.name }).contains("first_FlagsFlagRegistration")
    }

    @Test
    fun genericCustomMappersAreParameterized() {
        compile0(processors, emptyList(), """
            class GenericMapper<T> : OpenfeatureFlagMapper<T> {
                override fun map(features: Features, key: String, fallback: T, context: EvaluationContext): T = fallback
            }
        """, """
            @OpenfeatureSource("flags") interface Flags : Contextable<Flags> {
                @Mapping(GenericMapper::class) fun value(): Boolean = true
            }
        """).assertSuccess()
        assertThat(loadClass("$" + "Flags_Impl").constructors[0].genericParameterTypes[3].typeName)
            .endsWith("GenericMapper<java.lang.Boolean>")
    }

    @Test
    fun explicitProviderKeyKeepsConfigurationMethodName() {
        compile0(processors, emptyList(), """
            @OpenfeatureSource("flags") interface Flags {
                @OpenfeatureKey("vendor-checkout-v2") fun enabled(): Boolean = false
            }
        """).assertSuccess()
        val api = OpenFeatureAPI.createIsolated()
        try {
            api.setProviderAndWait(InMemoryProvider(mapOf("vendor-checkout-v2" to
                dev.openfeature.sdk.providers.memory.Flag.builder<Boolean>()
                    .variant("on", true).defaultVariant("on").build())))
            val instance = new("$" + "Flags_Impl", api.client, config(emptyMap()), ImmutableContext())
            assertThat(instance.javaClass.getMethod("enabled").invoke(instance)).isEqualTo(true)
            assertThat(loadClass("$" + "Flags_Config").getMethod("enabled")).isNotNull()
            val module = loadClass("$" + "Flags_Module")
            val implementation = Proxy.newProxyInstance(module.classLoader, arrayOf(module), InvocationHandler::invokeDefault)
            val registrar = module.getMethod("flagsFlagRegistration").invoke(implementation) as OpenfeatureFlagRegistrar
            assertThat(registrar.flags()).containsOnlyKeys("vendor-checkout-v2")
        } finally {
            api.shutdown()
        }
    }

    @Test
    fun jsonAndConvertedFlagsResolveThroughKoraGraph() {
        compile0(processors + JsonSymbolProcessorProvider() + KoraAppProcessorProvider(), emptyList(), """
            @io.koraframework.config.common.annotation.ConfigMapper
            @io.koraframework.json.common.annotation.Json
            data class Settings(val limit: Int)
        """, """
            enum class Mode { ON, OFF }
        """, """
            @OpenfeatureSource("flags") interface Flags {
                fun timeout(): java.time.Duration = java.time.Duration.ofSeconds(2)
                fun mode(): Mode = Mode.OFF
                @io.koraframework.json.common.annotation.Json
                fun settings(): Settings = Settings(5)
            }
        """, """
            @KoraApp
            interface App : OpenfeatureModule, io.koraframework.config.common.mapper.ConfigValueMapperModule,
                io.koraframework.json.common.JsonModule {
                fun config(): io.koraframework.config.common.Config =
                    io.koraframework.config.common.util.ConfigMappingUtils.fromMap(emptyMap<String, Any>())
                fun provider(): FeatureProvider = dev.openfeature.sdk.providers.memory.InMemoryProvider(mapOf(
                    "flags.timeout" to dev.openfeature.sdk.providers.memory.Flag.builder<String>()
                        .variant("value", "PT4S").defaultVariant("value").build(),
                    "flags.mode" to dev.openfeature.sdk.providers.memory.Flag.builder<String>()
                        .variant("value", "ON").defaultVariant("value").build(),
                    "flags.settings" to dev.openfeature.sdk.providers.memory.Flag.builder<Value>()
                        .variant("value", Value(ImmutableStructure(mapOf("limit" to Value(42)))))
                        .defaultVariant("value").build()
                ))
                @Root fun root(flags: Flags): String =
                    flags.timeout().toString() + ":" + flags.mode() + ":" + flags.settings().limit
            }
        """).assertSuccess()
        loadClass("AppGraph").toGraph().use { graph ->
            assertThat(graph.findByType(String::class.java)).isEqualTo("PT4S:ON:42")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "class Flags",
        "interface Flags<T> { fun value(): T }",
        "interface Flags { fun value(key: String): Int }",
        "interface Flags { fun value() }",
        "interface Flags { fun <T> value(): T }",
        "interface Flags { fun value(): String? }",
        "interface Flags { @OpenfeatureType(FlagValueType.BOOLEAN) fun value(): String }",
        "interface Flags { val enabled: Boolean }",
        "interface Flags { suspend fun value(): String }",
        "interface Flags { @OpenfeatureKey(\"\") fun value(): Boolean }",
        "interface Flags { @OpenfeatureKey(\"same\") fun first(): Boolean; @OpenfeatureKey(\"same\") fun second(): Boolean }"
    ])
    fun invalidDeclarationsReportProcessorErrors(declaration: String) {
        val result = compile0(listOf(OpenfeatureSymbolProcessorProvider()), emptyList(),
            "@OpenfeatureSource(\"flags\") " + declaration).assertFailure()
        assertThat(result.messages.joinToString("\n")).containsAnyOf("OpenFeature", "Openfeature", "@OpenfeatureSource")
    }
}
