package ru.tinkoff.kora.avro.symbol.processor

import org.apache.avro.generic.IndexedRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.Encoder
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificData
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.assertj.core.api.Assertions
import ru.tinkoff.kora.avro.common.AvroReader
import ru.tinkoff.kora.avro.common.AvroWriter
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.*

abstract class AbstractAvroSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.common.KoraApp;
            import tinkoff.kora.binary.TestAvroBinary;
            import tinkoff.kora.json.TestAvroJson;
            import ru.tinkoff.kora.avro.common.annotation.*;
            import ru.tinkoff.kora.avro.common.AvroReader;
            import ru.tinkoff.kora.avro.common.AvroWriter;
            """.trimIndent()
    }

    protected fun getTestAvroBinaryGenerated(): IndexedRecord {
        val testAvro = newGenerated("TestAvro").invoke() as IndexedRecord
        testAvro.put(0, "cluster")
        testAvro.put(1, Instant.EPOCH)
        testAvro.put(2, "descr")
        testAvro.put(3, 12345L)
        testAvro.put(4, true)
        return testAvro
    }

    protected fun getTestAvroJsonGenerated(): IndexedRecord {
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

    protected fun getTestAvroAsJson(): String {
        return ""
    }

    protected fun assertThatTestAvroValid(expected: IndexedRecord, actual: IndexedRecord) {
        Assertions.assertThat(actual).isNotNull()
        Assertions.assertThat(actual[0].toString()).isEqualTo(actual[0].toString())
        Assertions.assertThat(actual[1]).isEqualTo(expected[1])
        Assertions.assertThat(actual[2].toString()).isEqualTo(actual[2].toString())
        Assertions.assertThat(actual[3]).isEqualTo(expected[3])
        Assertions.assertThat(actual[4]).isEqualTo(expected[4])
    }

    protected fun getAvroJavaClass(): String {
        try {
            val strings = Files.lines(File("build/generated/sources/avro/tinkoff/kora/TestAvro.java").toPath())
                .map { s -> s.replace("tinkoff.kora.", "") }
                .toList()
            val avro = java.lang.String.join("\n", strings.subList(7, strings.size))
            return avro.replace("tinkoff.kora", testPackage())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    protected open fun readerBinaryClass(forClass: String) = compileResult.classLoader.readerBinaryClass(testPackage(), forClass)
    protected open fun writerBinaryClass(forClass: String) = compileResult.classLoader.writerBinaryClass(testPackage(), forClass)
    protected open fun readerJsonClass(forClass: String) = compileResult.classLoader.readerJsonClass(testPackage(), forClass)
    protected open fun writerJsonClass(forClass: String) = compileResult.classLoader.writerJsonClass(testPackage(), forClass)

    protected open fun <T : IndexedRecord> readerBinary(forClass: String, vararg params: Any?) = compileResult.classLoader.readerBinary<T>(testPackage(), forClass, *params)
    protected open fun <T : IndexedRecord> writerBinary(forClass: String, vararg params: Any?) = compileResult.classLoader.writerBinary<T>(testPackage(), forClass, *params)
    protected open fun <T : IndexedRecord> readerJson(forClass: String, vararg params: Any?) = compileResult.classLoader.readerJson<T>(testPackage(), forClass, *params)
    protected open fun <T : IndexedRecord> writerJson(forClass: String, vararg params: Any?) = compileResult.classLoader.writerJson<T>(testPackage(), forClass, *params)

    protected open fun <T : IndexedRecord> mapperClass(forClass: String) = compileResult.classLoader.mapper<T>(testPackage(), forClass)
    protected open fun <T : IndexedRecord> mapper(forClass: String, readerParams: List<*>, writerParams: List<*>) =
        compileResult.classLoader.mapper<T>(testPackage(), forClass, readerParams, writerParams)

    class ReaderAndWriter<T : IndexedRecord>(private val reader: AvroReader<T>, private val writer: AvroWriter<T>) : AvroReader<T>, AvroWriter<T> {

        override fun read(`is`: InputStream?): T = reader.read(`is`)

        override fun writeBytes(value: T): ByteArray = writer.writeBytes(value)
    }

    companion object {
        fun <T : IndexedRecord> ClassLoader.mapper(packageName: String, forClass: String): ReaderAndWriter<T> {
            return mapper(packageName, forClass, listOf<Any>(), listOf<Any>())
        }

        fun <T : IndexedRecord> ClassLoader.mapper(packageName: String, forClass: String, readerParams: List<*>, writerParams: List<*>): ReaderAndWriter<T> {
            val reader: AvroReader<T> = readerBinary(packageName, forClass, *readerParams.toTypedArray())
            val writer: AvroWriter<T> = writerBinary(packageName, forClass, *writerParams.toTypedArray())
            return ReaderAndWriter(reader, writer)
        }

        fun ClassLoader.readerBinaryClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroBinaryReader")!!

        fun ClassLoader.readerJsonClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroJsonReader")!!

        fun ClassLoader.writerBinaryClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroBinaryWriter")!!

        fun ClassLoader.writerJsonClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_AvroJsonWriter")!!

        fun <T : IndexedRecord> ClassLoader.readerBinary(packageName: String, forClass: String, vararg params: Any?): AvroReader<T> {
            return readerBinaryClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as AvroReader<T>
        }

        fun <T : IndexedRecord> ClassLoader.writerBinary(packageName: String, forClass: String, vararg params: Any?): AvroWriter<T> {
            return writerBinaryClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as AvroWriter<T>
        }

        fun <T : IndexedRecord> ClassLoader.readerJson(packageName: String, forClass: String, vararg params: Any?): AvroReader<T> {
            return readerJsonClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as AvroReader<T>
        }

        fun <T : IndexedRecord> ClassLoader.writerJson(packageName: String, forClass: String, vararg params: Any?): AvroWriter<T> {
            return writerJsonClass(packageName, forClass)
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

    // json
    protected fun writeAsJson(value: IndexedRecord): ByteArray {
        try {
            ByteArrayOutputStream().use { stream ->
                val writer = SpecificDatumWriter<Any>(value.schema)
                val jsonEncoder: Encoder = EncoderFactory.get().jsonEncoder(value.schema, stream)
                writer.write(value, jsonEncoder)
                jsonEncoder.flush()
                return stream.toByteArray()
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    // header
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

    // fields
    protected fun readAsBinary(value: ByteArray?): IndexedRecord {
        try {
            val fieldData = getTestAvroBinaryGenerated().javaClass.getDeclaredField("MODEL$")
            fieldData.isAccessible = true
            val data = fieldData[value] as SpecificData

            val reader = SpecificDatumReader<Any?>(getTestAvroBinaryGenerated().schema, getTestAvroBinaryGenerated().schema, data)
            val binaryDecoder = DecoderFactory.get().binaryDecoder(value, null)
            return reader.read(null, binaryDecoder) as IndexedRecord
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
