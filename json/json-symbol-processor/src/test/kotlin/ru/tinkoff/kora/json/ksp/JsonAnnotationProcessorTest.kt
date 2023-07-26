package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.*
import ru.tinkoff.kora.json.ksp.AbstractJsonSymbolProcessorTest.Companion.reader
import ru.tinkoff.kora.json.ksp.AbstractJsonSymbolProcessorTest.Companion.readerClass
import ru.tinkoff.kora.json.ksp.AbstractJsonSymbolProcessorTest.Companion.writer
import ru.tinkoff.kora.json.ksp.AbstractJsonSymbolProcessorTest.Companion.writerClass
import ru.tinkoff.kora.json.ksp.dto.*
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.ksp.common.symbolProcessJava
import java.io.IOException
import java.io.StringWriter
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

@KspExperimental
internal class JsonAnnotationProcessorTest {
    @Test
    fun testReadWriteAllSupportedTypes() {
        val cl = jsonClassLoader(DtoWithSupportedTypes::class)
        val reader: JsonReader<DtoWithSupportedTypes?> = cl.reader(
            DtoWithSupportedTypes::class.java,
            ListJsonReader { obj: JsonParser -> obj.intValue },
            SetJsonReader { obj: JsonParser -> obj.intValue }
        )
        val writer: JsonWriter<DtoWithSupportedTypes?> = cl.writer(
            DtoWithSupportedTypes::class.java, ListJsonWriter { obj: JsonGenerator, v: Int? ->
            obj.writeNumber(v!!)
        },
            SetJsonWriter { obj: JsonGenerator, v: Int? ->
                obj.writeNumber(
                    v!!
                )
            }
        )
        val obj = DtoWithSupportedTypes(
            "string", true,
            1, BigInteger.TEN, BigDecimal.TEN,
            0.4, 0.6f,
            100L, 10.toShort(), byteArrayOf(1, 2, 3),
            listOf(1), mutableSetOf(1)
        )
        val json = toJson(writer, obj)
        val parsed: DtoWithSupportedTypes = fromJson(reader, json)!!
        assertThat(toStringExcludeBinary(parsed)).isEqualTo(toStringExcludeBinary(obj))
    }

    @Test
    fun testDtoWithTypeAlias() {
        val cl = jsonClassLoader(DtoWithParametrizedTypeAlias::class)
        val readerClass = cl.loadReader(DtoWithParametrizedTypeAlias::class.java)
        val writerClass = cl.loadWriter(DtoWithParametrizedTypeAlias::class.java)
    }

    private fun <T : Any> jsonClassLoader(kClass: KClass<T>): JsonClassLoader {
        val kspCl = symbolProcess(kClass, JsonSymbolProcessorProvider())
        return JsonClassLoader(kspCl)
    }

    //
    @Test
    fun testNamingStrategy() {
        val cl1 = jsonClassLoader(DtoWithSnakeCaseNaming::class)
        val reader: JsonReader<DtoWithSnakeCaseNaming?> = cl1.reader(DtoWithSnakeCaseNaming::class.java)
        val writer: JsonWriter<DtoWithSnakeCaseNaming?> = cl1.writer(DtoWithSnakeCaseNaming::class.java)
        val json = """
            {
              "string_field" : "Test",
              "integer_field" : 5
            }""".trimIndent()
        val dto = DtoWithSnakeCaseNaming("Test", 5)
        val parsed: DtoWithSnakeCaseNaming = fromJson(reader, json)!!
        assertThat(dto).isEqualTo(parsed)
        assertThat(toJson(writer, dto)).isEqualTo(json)
    }


    private fun toStringExcludeBinary(o: Any): String {
        val str = o.toString()
        val bIndexOf = str.indexOf("=[B")
        if (bIndexOf < 0) {
            return str
        }
        val lastBIndex = str.indexOf(", ", bIndexOf)
        return str.substring(0, bIndexOf) + str.substring(lastBIndex)
    }


    @Test
    fun testReadWriteJsonFieldProperties() {
        val writer: WriterAndReader<DtoWithJsonFieldWriter> = processClass(DtoWithJsonFieldWriter::class)
        val json = toJson(writer, DtoWithJsonFieldWriter("field1", "field2", "field3", "field4"))
        val expectedJson: String = """
            {
              "renamedField1" : "field1",
              "renamedField2" : "field2",
              "field3" : -1,
              "field4" : -1
            }""".trimIndent()
        assertThat(json).isEqualTo(expectedJson)
        val newJson: String = """
            {
              "field0": "field0",
               "renamedField1" : "field1",
               "renamedField2" : "field2",
               "field3" : -1,
               "field4" : -1,
               "field5": [[[[{"field": "value"}]]]]
            }
            """.trimIndent()
        fromJson(writer, newJson)
    }

    @Test
    fun testWriteJsonSkip() {
        val writer = processClass(DtoWithJsonSkip::class)
        val json = toJson(writer, DtoWithJsonSkip("field1", "field2", "field3", "field4"))
        assertThat(json).isEqualTo(
            """
            {
              "field1" : "field1",
              "field2" : "field2"
            }""".trimIndent()
        )
    }


    @Test
    fun testWriteJsonInnerDto() {
        val cl = jsonClassLoader(DtoWithInnerDto::class)
        val innerReader = cl.reader(DtoWithInnerDto.InnerDto::class.java)
        val reader: JsonReader<DtoWithInnerDto?> = cl.reader(
            DtoWithInnerDto::class.java,
            innerReader,
            ListJsonReader(innerReader),
            MapJsonReader(innerReader),
            ListJsonReader(ListJsonReader(innerReader))
        )
        val innerWriter = cl.writer(DtoWithInnerDto.InnerDto::class.java)
        val writer = cl.writer(
            DtoWithInnerDto::class.java,
            innerWriter,
            ListJsonWriter(innerWriter),
            MapJsonWriter(innerWriter),
            ListJsonWriter(ListJsonWriter(innerWriter))
        )
        val obj = DtoWithInnerDto(
            DtoWithInnerDto.InnerDto("field1"),
            listOf(
                DtoWithInnerDto.InnerDto("field1"),
                DtoWithInnerDto.InnerDto("field2")
            ),
            mapOf("test" to DtoWithInnerDto.InnerDto("field3")),
            listOf(
                listOf(
                    DtoWithInnerDto.InnerDto("field5")
                )
            )
        )
        val json = toJson(writer, obj)
        assertThat(json).isEqualTo(
            """
            {
              "inner" : {
                "field1" : "field1"
              },
              "field2" : [ {
                "field1" : "field1"
              }, {
                "field1" : "field2"
              } ],
              "field3" : {
                "test" : {
                  "field1" : "field3"
                }
              },
              "field4" : [ [ {
                "field1" : "field5"
              } ] ]
            }""".trimIndent()
        )
        val parsed = fromJson(reader, json)
        assertThat(parsed).isEqualTo(obj)
    }

    @Test
    fun testOnlyReaderDto() {
        val reader = processClass(
            DtoOnlyReader::class
        )
        assertThat(reader.writer).isNull()
        val expected = DtoOnlyReader("field1", "field2", DtoOnlyReader.Inner("3"))
        val `object` =
            fromJson(
                reader, """
            {
              "field1" : "field1",
              "renamedField2" : "field2",
              "field3" : "3"
            }""".trimIndent()
            )
        assertThat(`object`).isEqualTo(expected)
    }

    @Test
    fun testOnlyWriterDto() {
        val writer = processClass(DtoOnlyWriter::class)
        assertThat(writer.reader).isNull()
        assertThat(writer.writer).isNotNull()
        val obj = DtoOnlyWriter("field1", "field2", DtoOnlyWriter.Inner("3"), "field4")
        val json = toJson(writer, obj)
        assertThat(json)
            .isEqualTo(
                """
            {
              "field1" : "field1",
              "renamedField2" : "field2",
              "field3" : "3"
            }""".trimIndent()
            )
    }


    @Test
    fun testWriteDtoJavaBeans() {
        val writer = processClass(DtoJavaBean::class)
        assertThat(writer.reader).isNull()
        val obj = DtoJavaBean("field1", 2)
        val json = toJson(writer, obj)
        assertThat(json).isEqualTo(
            """
            {
              "string_field" : "field1",
              "int_field" : 2
            }""".trimIndent()
        )
    }

    @Test
    fun testEmptyClass() {
        val writer = processClass(EmptyClass::class)
        assertThat(writer.reader).isNotNull
        assertThat(writer.writer).isNotNull
        val obj = EmptyClass()
        val json = toJson(writer, obj)
        assertThat(json).isEqualTo("{ }")
    }

    @Test
    fun testNullableBeans() {
        val reader = processClass(DtoWithNullableFields::class)
        assertThat(reader.writer).isNull()
        var expected: DtoWithNullableFields? = DtoWithNullableFields("field1", 4, "field2", null)
        var obj = fromJson(
            reader, """
            {
              "field_1" : "field1",
              "field2" : "field2",
              "field4" : 4
            }""".trimIndent()
        )
        assertThat(obj).isEqualTo(expected)
        expected = DtoWithNullableFields("field1", 4, null, null)
        obj = fromJson(
            reader, """
            {
              "field_1" : "field1",
              "field4" : 4
            }""".trimIndent()
        )
        assertThat(obj).isEqualTo(expected)
        expected = DtoWithNullableFields("field1", 4, null, null)
        obj = fromJson(
            reader, """
            {
              "field_1" : "field1",
              "field2" : null,
              "field4" : 4
            }""".trimIndent()
        )
        assertThat(obj).isEqualTo(expected)
        Assertions.assertThatThrownBy {
            fromJson(
                reader, """
            {
              "field2" : "field2"
            }""".trimIndent()
            )
        }
            .isInstanceOf(JsonParseException::class.java)
            .hasMessageStartingWith("Some of required json fields were not received: field1(field_1)")
        Assertions.assertThatThrownBy {
            fromJson(
                reader, """
            {
              "field_1" : "field1",
              "field2" : "field2",
              "field4" : null
            }""".trimIndent()
            )
        }
            .isInstanceOf(JsonParseException::class.java)
            .hasMessageStartingWith("Expecting [VALUE_NUMBER_INT] token for field 'field4', got VALUE_NULL")
    }

    @Test
    fun testKotlinDataClassDto() {
        val jsoner = jsonClassLoader(KotlinDataClassDtoWithNonPrimaryConstructor::class)
        val reader = jsoner.reader(KotlinDataClassDtoWithNonPrimaryConstructor::class.java)
        val writer = jsoner.writer(KotlinDataClassDtoWithNonPrimaryConstructor::class.java)
        val obj = KotlinDataClassDtoWithNonPrimaryConstructor("field1", "field2")
        val json = toJson(writer, obj)
        val expectedJson = """
            {
              "field1" : "field1",
              "field2" : "field2"
            }""".trimIndent()
        assertThat(json).isEqualTo(expectedJson)
        val newJson = """
            {
              "field1" : "field1",
              "field2" : null
            }""".trimIndent()
        val newObject = fromJson(reader, newJson)
        assertThat(newObject).isEqualTo(KotlinDataClassDtoWithNonPrimaryConstructor("field1", null))
        Assertions.assertThatThrownBy {
            fromJson(
                reader, """
            {
              "field1" : null,
              "field2" : null
            }""".trimIndent()
            )
        }
            .isInstanceOf(JsonParseException::class.java)
            .hasMessageStartingWith("Expecting [VALUE_STRING] token for field 'field1', got VALUE_NULL")
    }


    @Test
    fun testJavaRecord() {
        val cl = JsonClassLoader(symbolProcessJava(JavaRecordDto::class.java, JsonSymbolProcessorProvider()))
        val intReader = JsonReader<Int> { it.intValue }
        val booleanReader = JsonReader<Boolean> { it.booleanValue }
        val stringJsonReader: JsonReader<String> = JsonReader { parser: JsonParser -> parser.text }
        val reader: JsonReader<JavaRecordDto?> = cl.reader(
            JavaRecordDto::class.java,
            stringJsonReader,
            intReader,
            booleanReader
        )
        val writer: JsonWriter<JavaRecordDto?> = cl.writer(
            JavaRecordDto::class.java
        )

        val obj = JavaRecordDto("value1", 1, false)

        val json = toJson(writer, obj)
        val expectedJson = """
            {
              "field1" : "value1",
              "integer" : 1
            }""".trimIndent()
        assertThat(json).isEqualTo(expectedJson)
        val jsonForRead = """
            {
              "field1" : "value1",
              "integer" : 1,
              "bool" : false
            }""".trimIndent()
        val parsed = fromJson(reader, jsonForRead)
        assertThat(parsed).isEqualTo(obj)

    }

    @Test
    fun test31Field() {
        val cl = JsonClassLoader(symbolProcess(DtoWith31Fields::class, JsonSymbolProcessorProvider()))
        cl.reader(DtoWith31Fields::class.java)
    }

    @Test
    fun test32Field() {
        val cl = JsonClassLoader(symbolProcess(DtoWith32Fields::class, JsonSymbolProcessorProvider()))
        cl.reader(DtoWith32Fields::class.java)
    }

    private fun <T> toJson(writer: JsonWriter<T>, fromJson: T): String {
        val jf = JsonFactory(JsonFactoryBuilder())
        val sw = StringWriter()
        try {
            jf.createGenerator(sw).use { gen ->
                gen.prettyPrinter = DefaultPrettyPrinter()
                writer.write(gen, fromJson)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return sw.toString()
    }

    private fun <T> fromJson(reader: JsonReader<T>, json: String): T {
        val jf = JsonFactory(JsonFactoryBuilder())
        jf.createParser(json).use { parser ->
            parser.nextToken()
            return reader.read(parser)!!
        }
    }


    private inner class WriterAndReader<T>(
        val writer: JsonWriter<T?>?,
        val reader: JsonReader<T?>?
    ) : JsonWriter<T?>, JsonReader<T?> {
        override fun read(parser: JsonParser): T? {
            return this.reader?.read(parser)
        }

        override fun write(gen: JsonGenerator, `object`: T?) {
            this.writer?.write(gen, `object`)
        }
    }

    private fun <T : Any> processClass(type: KClass<T>): WriterAndReader<T> {
        val cl = jsonClassLoader(type)
        val writer: JsonWriter<T?>? = try {
            cl.writer(type.java)
        } catch (e: Exception) {
            null
        }
        val reader: JsonReader<T?>? = try {
            cl.reader(type.java)
        } catch (e: Exception) {
            null
        }
        return WriterAndReader(writer, reader)
    }

    //
    @Suppress("UNCHECKED_CAST")
    private class JsonClassLoader(private val cl: ClassLoader) {
        fun <T> writer(type: Class<T>, vararg args: Any?) = cl.writer(type.packageName, fullName(type), *args) as JsonWriter<T?>
        fun <T> reader(type: Class<T>, vararg args: Any?) = cl.reader(type.packageName, fullName(type), *args) as JsonReader<T?>

        fun <T> loadWriter(type: Class<T>) = cl.writerClass(type.packageName, fullName(type)) as Class<JsonWriter<T>>
        fun <T> loadReader(type: Class<T>) = cl.readerClass(type.packageName, fullName(type)) as Class<JsonWriter<T>>

        private fun fullName(type: Class<*>): String {
            val name = StringBuilder(type.simpleName)
            var parent = type.declaringClass
            while (parent != null) {
                name.insert(0, parent.simpleName + "_")
                parent = parent.declaringClass
            }
            return name
                .toString()
        }
    }
}
