package ru.tinkoff.kora.avro.symbol.processor

import org.apache.avro.generic.IndexedRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.avro.common.AvroReader
import ru.tinkoff.kora.avro.common.AvroWriter
import tinkoff.kora.json.TestAvroJson
import java.time.Instant

class AvroJsonTests : AbstractAvroSymbolProcessorTest() {

    @Test
    fun testReaderAndWriterFromExtension() {
        compile0(
            """
                @KoraApp
                interface TestApp {
                    @Root
                    fun root(@AvroJson r: AvroReader<TestAvroJson>, @AvroJson w: AvroWriter<TestAvroJson>) = ""
                }
                
                """.trimIndent()
        )

        compileResult.assertSuccess()
        val reader: AvroReader<IndexedRecord> = compileResult.classLoader.readerJson("tinkoff.kora.json", "TestAvroJson")
        val writer: AvroWriter<IndexedRecord> = compileResult.classLoader.writerJson("tinkoff.kora.json", "TestAvroJson")
        assertThat(reader).isNotNull()
        assertThat(writer).isNotNull()

        val testAvro: IndexedRecord = TestAvroJson.newBuilder()
            .setCluster("cluster")
            .setDate(Instant.EPOCH)
            .setDescription("descr")
            .setCounter(12345L)
            .setFlag(true)
            .build()
        val write = writer.writeBytes(testAvro)
        val read = reader.readUnchecked(write)
        assertThatTestAvroValid(testAvro, read)
    }
}
