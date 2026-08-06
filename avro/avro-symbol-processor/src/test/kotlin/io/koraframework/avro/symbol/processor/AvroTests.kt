package io.koraframework.avro.symbol.processor

import iokoraframework.kora.avro.TestAvro
import org.apache.avro.generic.IndexedRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.avro.common.AvroReader
import io.koraframework.avro.common.AvroWriter
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import java.time.Instant

class AvroTests : AbstractAvroSymbolProcessorTest() {

    @Test
    fun testReaderAndWriterFromExtension() {
        compile0(
            listOf(KoraAppProcessorProvider(),  AvroSymbolProcessorProvider()),
            """
                @KoraApp
                interface TestApp {
                    @Root
                    fun root(@Avro r: AvroReader<TestAvro>, @Avro w: AvroWriter<TestAvro>) = ""
                }
                """.trimIndent()
        )

        compileResult.assertSuccess()
        val reader: AvroReader<IndexedRecord> = compileResult.assertSuccess().classLoader.reader("iokoraframework.kora.avro", "TestAvro")
        val writer: AvroWriter<IndexedRecord> = compileResult.assertSuccess().classLoader.writer("iokoraframework.kora.avro", "TestAvro")
        assertThat(reader).isNotNull()
        assertThat(writer).isNotNull()

        val testAvro: IndexedRecord = TestAvro.newBuilder()
            .setCluster("cluster")
            .setDate(Instant.EPOCH)
            .setDescription("descr")
            .setCounter(12345L)
            .setFlag(true)
            .build()
        val write = writer.writeBytes(testAvro)
        val read = reader.readUnchecked(write)
        assertThatTestAvroValid(testAvro, read!!)
    }
}
