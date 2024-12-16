package ru.tinkoff.kora.avro.symbol.processor

import org.apache.avro.generic.IndexedRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.avro.common.AvroReader
import ru.tinkoff.kora.avro.common.AvroWriter
import tinkoff.kora.binary.TestAvroBinary
import java.time.Instant

class AvroBinaryTests : AbstractAvroSymbolProcessorTest() {

    @Test
    fun testReaderAndWriterFromExtension() {
        compile0(
            """
                @KoraApp
                interface TestApp {
                    @Root
                    fun root(@AvroBinary r: AvroReader<TestAvroBinary>, @AvroBinary w: AvroWriter<TestAvroBinary>) = ""
                }
                
                """.trimIndent()
        )

        compileResult.assertSuccess()
        val reader: AvroReader<IndexedRecord> = compileResult.classLoader.readerBinary("tinkoff.kora.binary", "TestAvroBinary")
        val writer: AvroWriter<IndexedRecord> = compileResult.classLoader.writerBinary("tinkoff.kora.binary", "TestAvroBinary")
        assertThat(reader).isNotNull()
        assertThat(writer).isNotNull()

        val testAvro: IndexedRecord = TestAvroBinary.newBuilder()
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
