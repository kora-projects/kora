package ru.tinkoff.kora.json.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph
import java.nio.charset.StandardCharsets

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
    fun testSealedInterface0() {
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

        m.assertRead("""
            { 
              "array": [1, 2, 3],
              "object": {
                  "@type": 123,
                  "test": 42
              },
              "@type":"Impl1",
              "value":"test"
            }
            """.trimIndent(), new("TestInterface\$Impl1", "test"))
    }

    @Test
    fun testSealedInterfaceWithField() {
        compile(
            """
            @Json
            @JsonDiscriminatorField("@type")
            sealed interface TestInterface {
                @Json
                @JsonDiscriminatorValue(value = ["Impl1.1", "Impl1.2"])
                data class Impl1(@JsonField("@type") val type: String, val value: String) : TestInterface {
                    init {
                        if ("Impl1.1" != type && "Impl1.2" != type) {
                          throw IllegalStateException(type)
                        }
                    }
                }

                @Json
                data class Impl2(val value: Int) : TestInterface

                @Json
                @JsonDiscriminatorValue(value = ["Impl3.1", "Impl3.2"])
                class Impl3 : TestInterface {
                    override fun equals(other: Any?) = other is Impl3
                }
            }
            """.trimIndent()
        )
        val o11 = new("TestInterface\$Impl1", "Impl1.1", "test");
        val json11 = "{\"@type\":\"Impl1.1\",\"value\":\"test\"}";
        val o12 = new("TestInterface\$Impl1", "Impl1.2", "test");
        val json12 = "{\"@type\":\"Impl1.2\",\"value\":\"test\"}";

        val o2 = new("TestInterface\$Impl2", 42);
        val json2 = "{\"@type\":\"Impl2\",\"value\":42}";
        val o3 = new("TestInterface\$Impl3");
        val json31 = "{\"@type\":\"Impl3.1\"}";
        val json32 = "{\"@type\":\"Impl3.2\"}";

        val m1 = mapper("TestInterface_Impl1");
        val m2 = mapper("TestInterface_Impl2");
        val m3 = mapper("TestInterface_Impl3");
        val m = mapper("TestInterface", listOf(m1, m2, m3), listOf(m1, m2, m3));

        assertThat(m.toByteArray(o11)).asString(StandardCharsets.UTF_8).isEqualTo(json11);
        assertThat(m.toByteArray(o12)).asString(StandardCharsets.UTF_8).isEqualTo(json12);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.toByteArray(o3)).asString(StandardCharsets.UTF_8).isEqualTo(json31);
        assertThat(m.read(json11.toByteArray(StandardCharsets.UTF_8))).isEqualTo(o11);
        assertThat(m.read(json12.toByteArray(StandardCharsets.UTF_8))).isEqualTo(o12);
        assertThat(m.read(json2.toByteArray(StandardCharsets.UTF_8))).isEqualTo(o2);
        assertThat(m.read(json31.toByteArray(StandardCharsets.UTF_8))).isEqualTo(o3);
        assertThat(m.read(json32.toByteArray(StandardCharsets.UTF_8))).isEqualTo(o3);
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
