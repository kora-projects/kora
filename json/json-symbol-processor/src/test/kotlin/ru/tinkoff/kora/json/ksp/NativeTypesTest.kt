package ru.tinkoff.kora.json.ksp

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import java.net.URI

class NativeTypesTest : AbstractJsonSymbolProcessorTest() {

    private val uriReader = JsonReader<URI> { parser -> URI.create(parser.text) }
    private val uriWriter = JsonWriter<URI> { generator, `object` -> generator.writeString(`object`.toString()) }

    @Test
    fun testNativeJavaNet() {
        compile0(
            listOf(JsonSymbolProcessorProvider()),
            """
            @Json
            data class TestRecord(val value: java.net.URI)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = mapper(
            "TestRecord",
            listOf(uriReader),
            listOf(uriWriter)
        )

        mapper.assert(new("TestRecord", URI.create("/some")), "{\"value\":\"/some\"}")
    }

    @Test
    fun testNativeJavaNetReaderExtension() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @JsonWriter
            data class TestRecord @JsonReader constructor (
              @JsonField("default")
              val value: java.net.URI
            )
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
              @Root
              fun root(reader: ru.tinkoff.kora.json.common.JsonReader<TestRecord>) = ""
            }
            """.trimIndent()
        )
        compileResult.isFailed()
    }

    @Test
    fun testNativeJavaNetWriterExtension() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @Json
            data class TestRecord(val value: java.net.URI)
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
              @Root
              fun root(writer: ru.tinkoff.kora.json.common.JsonWriter<TestRecord>) = ""
            }
            """.trimIndent()
        )
        compileResult.isFailed()
    }
}
