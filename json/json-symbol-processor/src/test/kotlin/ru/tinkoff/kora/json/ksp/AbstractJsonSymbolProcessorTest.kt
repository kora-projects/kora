package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.nio.charset.StandardCharsets

abstract class AbstractJsonSymbolProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.json.common.annotation.*;
            import java.util.Optional;
            
            """.trimIndent()
    }

    protected open fun compile(@Language("kotlin") vararg sources: String) {
        val compileResult = compile(listOf(JsonSymbolProcessorProvider()), *sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }
    }

    protected open fun readerClass(forClass: String) = compileResult.classLoader.readerClass(testPackage(), forClass)
    protected open fun writerClass(forClass: String) = compileResult.classLoader.writerClass(testPackage(), forClass)

    protected open fun reader(forClass: String, vararg params: Any?) = compileResult.classLoader.reader(testPackage(), forClass, params)
    protected open fun writer(forClass: String, vararg params: Any?) = compileResult.classLoader.writer(testPackage(), forClass, params)

    protected open fun mapper(forClass: String) = compileResult.classLoader.mapper(testPackage(), forClass)
    protected open fun mapper(forClass: String, readerParams: List<*>, writerParams: List<*>) = compileResult.classLoader.mapper(testPackage(), forClass, readerParams, writerParams)


    class ReaderAndWriter<T>(private val reader: JsonReader<T>, private val writer: JsonWriter<T>) :
        JsonReader<T>, JsonWriter<T> {
        override fun read(parser: JsonParser): T? {
            return reader.read(parser)
        }

        override fun write(generator: JsonGenerator, `object`: T?) {
            writer.write(generator, `object`)
        }
    }

    companion object {
        fun ClassLoader.mapper(packageName: String, forClass: String): ReaderAndWriter<Any?> {
            return mapper(packageName, forClass, listOf<Any>(), listOf<Any>())
        }

        fun ClassLoader.mapper(packageName: String, forClass: String, readerParams: List<*>, writerParams: List<*>): ReaderAndWriter<Any?> {
            val reader = reader(packageName, forClass, *readerParams.toTypedArray())
            val writer = writer(packageName, forClass, *writerParams.toTypedArray())
            return ReaderAndWriter(reader, writer)
        }

        fun ClassLoader.readerClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_JsonReader")!!
        fun ClassLoader.writerClass(packageName: String, forClass: String) = loadClass(packageName + ".$" + forClass + "_JsonWriter")!!

        fun ClassLoader.reader(packageName: String, forClass: String, vararg params: Any?): JsonReader<Any?> {
            return readerClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as JsonReader<Any?>
        }

        fun ClassLoader.writer(packageName: String, forClass: String, vararg params: Any?): JsonWriter<Any?> {
            return writerClass(packageName, forClass)
                .constructors[0]
                .newInstance(*params) as JsonWriter<Any?>
        }

        fun <T> ReaderAndWriter<T>.assert(value: T, json: String) {
            this.assertWrite(value, json)
            this.assertRead(json, value)
        }

        fun <T> JsonWriter<T>.assertWrite(value: T, expectedJson: String) {
            Assertions.assertThat(this.toByteArray(value)).asString(StandardCharsets.UTF_8).isEqualTo(expectedJson)
        }

        fun <T> JsonReader<T>.assertRead(json: String, expectedObject: T) {
            Assertions.assertThat(this.read(json.toByteArray())).isEqualTo(expectedObject)
        }
    }
}
