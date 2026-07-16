package io.koraframework.avro.symbol.processor

import io.koraframework.avro.common.AvroReader
import io.koraframework.avro.common.AvroWriter
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.apache.avro.generic.IndexedRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.Encoder
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificData
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.assertj.core.api.Assertions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

abstract class AbstractAvroSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.common.annotation.KoraApp;
            import iokoraframework.kora.avro.TestAvro;
            import io.koraframework.avro.common.annotation.*;
            import io.koraframework.avro.common.AvroReader;
            import io.koraframework.avro.common.AvroWriter;
            """.trimIndent()
    }

    protected fun getTestAvroGeneratedRecord(): IndexedRecord {
        val testAvro = newGenerated("TestAvro").invoke() as IndexedRecord
        testAvro.put(0, "cluster")
        testAvro.put(1, Instant.EPOCH)
        testAvro.put(2, "descr")
        testAvro.put(3, 12345L)
        testAvro.put(4, true)
        return testAvro
    }

    protected fun getTestAvroAsBytes(): ByteArray {
        return Base64.getDecoder().decode("DmNsdXN0ZXICAAIKZGVzY3IC8sABAgE=")
    }

    protected fun assertThatTestAvroValid(expected: IndexedRecord, actual: IndexedRecord) {
        Assertions.assertThat(actual).isNotNull()
        Assertions.assertThat(actual[0].toString()).isEqualTo(expected[0].toString())
        Assertions.assertThat(actual[1]).isEqualTo(expected[1])
        Assertions.assertThat(actual[2].toString()).isEqualTo(expected[2].toString())
        Assertions.assertThat(actual[3]).isEqualTo(expected[3])
        Assertions.assertThat(actual[4]).isEqualTo(expected[4])
    }

    protected open fun readerClass(forClass: String) = compileResult.assertSuccess().classLoader.readerClass(testPackage(), forClass)
    protected open fun writerClass(forClass: String) = compileResult.assertSuccess().classLoader.writerClass(testPackage(), forClass)

    protected open fun <T : IndexedRecord> reader(forClass: String, vararg params: Any?) =
        compileResult.assertSuccess().classLoader.reader<T>(testPackage(), forClass, *params)

    protected open fun <T : IndexedRecord> writer(forClass: String, vararg params: Any?) =
        compileResult.assertSuccess().classLoader.writer<T>(testPackage(), forClass, *params)

    protected open fun <T : IndexedRecord> mapperClass(forClass: String) =
        compileResult.assertSuccess().classLoader.mapper<T>(testPackage(), forClass)

    protected open fun <T : IndexedRecord> mapper(forClass: String, readerParams: List<*>, writerParams: List<*>) =
        compileResult.assertSuccess().classLoader.mapper<T>(testPackage(), forClass, readerParams, writerParams)

    class ReaderAndWriter<T : IndexedRecord>(private val reader: AvroReader<T>, private val writer: AvroWriter<T>) : AvroReader<T>, AvroWriter<T> {

        override fun read(`is`: InputStream?): T? = reader.read(`is`)

        override fun writeBytes(value: T?): ByteArray? = writer.writeBytes(value)
    }

    companion object {
        fun <T : IndexedRecord> ClassLoader.mapper(packageName: String, forClass: String): ReaderAndWriter<T> {
            return mapper(packageName, forClass, listOf<Any>(), listOf<Any>())
        }

        fun <T : IndexedRecord> ClassLoader.mapper(packageName: String, forClass: String, readerParams: List<*>, writerParams: List<*>): ReaderAndWriter<T> {
            val reader: AvroReader<T> = reader(packageName, forClass, *readerParams.toTypedArray())
            val writer: AvroWriter<T> = writer(packageName, forClass, *writerParams.toTypedArray())
            return ReaderAndWriter(reader, writer)
        }

        fun ClassLoader.readerClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroReader")!!

        fun ClassLoader.writerClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroWriter")!!

        fun <T : IndexedRecord> ClassLoader.reader(packageName: String, forClass: String, vararg params: Any?): AvroReader<T> {
            return readerClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as AvroReader<T>
        }

        fun <T : IndexedRecord> ClassLoader.writer(packageName: String, forClass: String, vararg params: Any?): AvroWriter<T> {
            return writerClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as AvroWriter<T>
        }

        fun <T : IndexedRecord> ReaderAndWriter<T>.assert(value: T, avro: String) {
            this.assertWrite(value, avro)
            this.assertRead(avro, value)
        }

        fun <T : IndexedRecord> AvroWriter<T>.assertWrite(value: T, expectedAvro: String) {
            Assertions.assertThat(this.writeBytes(value)).asString(StandardCharsets.UTF_8).isEqualTo(expectedAvro)
        }

        fun <T : IndexedRecord> AvroReader<T>.assertRead(avro: String, expectedObject: T) {
            Assertions.assertThat(this.read(avro.toByteArray())).isEqualTo(expectedObject)
        }
    }

    protected fun writeAsBinary(value: IndexedRecord): ByteArray {
        try {
            ByteArrayOutputStream().use { stream ->
                val fieldData = value.javaClass.getDeclaredField("MODEL$")
                fieldData.isAccessible = true
                val data = fieldData[value] as SpecificData

                val writer = SpecificDatumWriter<Any>(value.schema, data)
                val encoder: Encoder = EncoderFactory.get().directBinaryEncoder(stream, null)
                writer.write(value, encoder)
                encoder.flush()
                return stream.toByteArray()
            }
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    protected fun readAsBinary(value: ByteArray?): IndexedRecord {
        try {
            val generated = getTestAvroGeneratedRecord()
            val fieldData = generated.javaClass.getDeclaredField("MODEL$")
            fieldData.isAccessible = true
            val data = fieldData[generated] as SpecificData

            val reader = SpecificDatumReader<Any?>(generated.schema, generated.schema, data)
            val binaryDecoder = DecoderFactory.get().binaryDecoder(value, null)
            return reader.read(null, binaryDecoder) as IndexedRecord
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
