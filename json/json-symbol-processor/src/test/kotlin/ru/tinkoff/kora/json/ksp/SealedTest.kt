package ru.tinkoff.kora.json.ksp

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class SealedTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testSealedInterface() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @Json
                data class Impl1(val value: String) : TestInterface
                @Json
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent()
        )

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assert(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        m.assert(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }

    @Test
    fun testSealedAbstractClass() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed class TestInterface {
                @Json
                data class Impl1(val value: String) : TestInterface()
                @Json
                data class Impl2(val value: Int) : TestInterface()
            }
            """.trimIndent()
        )

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assert(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        m.assert(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }

    @Test
    fun testSealedInterfaceParsingType() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @Json
                data class Impl1(val value :String): TestInterface
                @Json
                data class Impl2(val value: Int): TestInterface
            }
            """
        );

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assertRead("{\"value\":\"test\", \"@type\":\"Impl1\"}", new("TestInterface\$Impl1", "test"))
        m.assertRead("{\"value\":42, \"@type\":\"Impl2\"}", new("TestInterface\$Impl2", 42))
    }

    @Test
    fun testSealedSubinterface() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                sealed interface Subinterface : TestInterface
                @Json
                data class Impl1(val value: String) : Subinterface
                @Json
                data class Impl2(val value: Int) : Subinterface
            }
            """.trimIndent()
        )

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assert(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        m.assert(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }

    @Test
    fun testExplicitDiscriminator() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @JsonDiscriminatorValue("type_1")
                @Json
                data class Impl1(val value: String) : TestInterface
                @Json
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent()
        )

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assert(new("TestInterface\$Impl1", "test"), "{\"@type\":\"type_1\",\"value\":\"test\"}")
        m.assert(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }

    @Test
    fun testSealedWithGeneric() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface <A> {
                @Json
                data class Impl1<A>(val value: String) : TestInterface<A>
                @Json
                data class Impl2<A>(val value: Int) : TestInterface<A>
            }
            """.trimIndent()
        )

        val m1 = mapper("TestInterface_Impl1")
        val m2 = mapper("TestInterface_Impl2")
        val m = mapper(
            "TestInterface",
            listOf(m1, m2),
            listOf(m1, m2)
        )

        m.assert(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        m.assert(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }

    @Test
    fun testSealedInterfaceJsonReaderExtension() {
        compile(
            listOf(KoraAppProcessorProvider()),
            """
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                data class Impl1(val value: String) : TestInterface
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp {
                  @Root
                  fun root(w: ru.tinkoff.kora.json.common.JsonReader<TestInterface>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val reader = graph.findByType(readerClass("TestInterface")) as JsonReader<Any?>

        reader.assertRead("{\"@type\":\"Impl1\",\"value\":\"test\"}", new("TestInterface\$Impl1", "test"))
        reader.assertRead("{\"@type\":\"Impl2\",\"value\":42}", new("TestInterface\$Impl2", 42))
    }

    @Test
    fun testSealedInterfaceJsonWriterExtension() {
        compile(
            listOf(KoraAppProcessorProvider()),
            """
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                data class Impl1(val value: String) : TestInterface
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp {
                  @Root
                  fun root(w: ru.tinkoff.kora.json.common.JsonWriter<TestInterface>) = ""
                }
            """.trimIndent()
        )

        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findByType(writerClass("TestInterface")) as JsonWriter<Any>

        writer.assertWrite(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        writer.assertWrite(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }


    @Test
    fun testSealedInterfaceJsonReaderExtensionWithProcessor() {
        compile(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @Json
                data class Impl1(val value: String) : TestInterface
                @Json
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp {
                  @Root
                  fun root(w: ru.tinkoff.kora.json.common.JsonReader<TestInterface>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val reader = graph.findByType(readerClass("TestInterface")) as JsonReader<Any?>

        reader.assertRead("{\"@type\":\"Impl1\",\"value\":\"test\"}", new("TestInterface\$Impl1", "test"))
        reader.assertRead("{\"@type\":\"Impl2\",\"value\":42}", new("TestInterface\$Impl2", 42))
    }

    @Test
    fun testSealedInterfaceJsonWriterExtensionWithProcessor() {
        compile(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @Json
                data class Impl1(val value: String) : TestInterface
                @Json
                data class Impl2(val value: Int) : TestInterface
            }
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp {
                  @Root
                  fun root(w: ru.tinkoff.kora.json.common.JsonWriter<TestInterface>) = ""
                }
            """.trimIndent()
        )

        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findByType(writerClass("TestInterface")) as JsonWriter<Any>

        writer.assertWrite(new("TestInterface\$Impl1", "test"), "{\"@type\":\"Impl1\",\"value\":\"test\"}")
        writer.assertWrite(new("TestInterface\$Impl2", 42), "{\"@type\":\"Impl2\",\"value\":42}")
    }
}
