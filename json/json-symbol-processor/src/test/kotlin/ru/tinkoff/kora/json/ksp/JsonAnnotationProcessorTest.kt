package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.*
import ru.tinkoff.kora.json.ksp.dto.*
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.ksp.common.symbolProcessJava
import java.io.IOException
import java.io.StringWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
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
            LocalDate.of(2020, 3, 15), listOf(1), mutableSetOf(1)
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

    @Test
    fun testDtoWithTypeParams() {
        val intReader = JsonReader<Int> {
            it.intValue
        }
        val stringJsonReader: JsonReader<String> = JsonReader { parser: JsonParser -> parser.text }
        val cl = jsonClassLoader(DtoWithTypeParam::class)
        val secondReader = cl.reader(DtoWithTypeParam.SecondTpe::class.java, intReader)
        val reader = cl.reader(
            DtoWithTypeParam::class.java,
            cl.reader(DtoWithTypeParam.FirstTpe::class.java, intReader, stringJsonReader),
            cl.reader(DtoWithTypeParam.SecondTpe::class.java, intReader),
            cl.reader(DtoWithTypeParam.ThirdTpe::class.java, stringJsonReader)
        )


        val intWriter = JsonWriter { gen: JsonGenerator, obj: Int? -> gen.writeNumber(obj!!) }
        val stringWriter = JsonWriter { gen: JsonGenerator, obj: String? -> gen.writeString(obj) }
        val writer = cl.writer(
            DtoWithTypeParam::class.java,
            cl.writer(
                DtoWithTypeParam.FirstTpe::class.java, intWriter, stringWriter
            ),
            cl.writer(DtoWithTypeParam.SecondTpe::class.java, intWriter),
            cl.writer(DtoWithTypeParam.ThirdTpe::class.java, stringWriter)
        )

        val expected1 = DtoWithTypeParam.FirstTpe(1, "a", 2)
        assertThat(expected1).isEqualTo(fromJson(reader, toJson(writer, expected1)))

        val expected2 = DtoWithTypeParam.SecondTpe(1)
        assertThat(expected2).isEqualTo(fromJson(reader, toJson(writer, expected2)))

        val expected3 = DtoWithTypeParam.ThirdTpe("a")
        assertThat(expected3).isEqualTo(fromJson(reader, toJson(writer, expected3)))
    }

    @Test
    fun testSealedClassDto() {
        val cl1 = jsonClassLoader(SealedClassDto::class)
        val reader: JsonReader<SealedClassDto?> = cl1.reader(
            SealedClassDto::class.java, cl1.reader(
                SealedClassDto.FirstDto::class.java, cl1.reader(SealedClassDto.FirstDto.InnerDto::class.java)
            ), cl1.reader(
                SealedClassDto.SecondDto::class.java
            )
        )
        val writer: JsonWriter<SealedClassDto?> = cl1.writer(
            SealedClassDto::class.java, cl1.writer(
                SealedClassDto.FirstDto::class.java, cl1.writer(SealedClassDto.FirstDto.InnerDto::class.java)
            ), cl1.writer(
                SealedClassDto.SecondDto::class.java
            )
        )
        val firstJson = """
            {
              "innerDto" : { "innerValue" : "val" },
              "@type" : "FirstDto",
              "commonValue" : "value1",
              "firstValue" : "value2"    
            }
           """.trimIndent()
        val secondJson = """
            {
              "@type" : "SecondDto",
              "firstValue" : "value",
              "secondValue" : 23,
              "commonValue" : "second"
            }""".trimIndent()

        val firstDto: SealedClassDto.FirstDto = SealedClassDto.FirstDto("value1", "value2", SealedClassDto.FirstDto.InnerDto("val"))
        val secondDto: SealedClassDto.SecondDto = SealedClassDto.SecondDto("value", 23)
        val firstParsed: SealedClassDto = fromJson(reader, firstJson)!!
        val secondParsed: SealedClassDto = fromJson(reader, secondJson)!!
        val firstWrittenJson: String = """
            {
              "@type" : "FirstDto",
              "commonValue" : "value1",
              "firstValue" : "value2",
              "innerDto" : {
                "innerValue" : "val"
              }
            }""".trimIndent()
        assertThat(firstDto).isEqualTo(firstParsed)
        assertThat(secondDto).isEqualTo(secondParsed)
        assertThat(toJson(writer, firstDto)).isEqualTo(firstWrittenJson)
        assertThat(toJson(writer, secondDto)).isEqualTo(secondJson)
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


    @Test
    fun testKotlinSealedInterfaceDto() {
        val cl1 = jsonClassLoader(KotlinSealedInterfaceDto::class)
        val reader: JsonReader<KotlinSealedInterfaceDto?> = cl1.reader(
            KotlinSealedInterfaceDto::class.java, cl1.reader(
                KotlinSealedInterfaceDto.FirstDto::class.java
            ), cl1.reader(KotlinSealedInterfaceDto.SecondDto::class.java)
        )
        val writer: JsonWriter<KotlinSealedInterfaceDto?> = cl1.writer(
            KotlinSealedInterfaceDto::class.java, cl1.writer(
                KotlinSealedInterfaceDto.FirstDto::class.java
            ), cl1.writer(KotlinSealedInterfaceDto.SecondDto::class.java)
        )
        val firstDto: KotlinSealedInterfaceDto.FirstDto = KotlinSealedInterfaceDto.FirstDto("common", "first")
        val secondDto: KotlinSealedInterfaceDto.SecondDto = KotlinSealedInterfaceDto.SecondDto("s", 22)
        val firstParsed = toJson(writer, firstDto)
        val secondParsed = toJson(writer, secondDto)
        assertThat(firstDto).isEqualTo(fromJson(reader, firstParsed))
        assertThat(secondDto).isEqualTo(fromJson(reader, secondParsed))
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
    fun testEnum() {
        val cl = jsonClassLoader(DtoWithEnum::class)
        val reader = cl.reader(
            DtoWithEnum::class.java,
            cl.reader(DtoWithEnum.TestEnum::class.java)
        )
        val writer = cl.writer(
            DtoWithEnum::class.java,
            cl.writer(DtoWithEnum.TestEnum::class.java)
        )
        val expected = DtoWithEnum(DtoWithEnum.TestEnum.VAL1)
        val json: String = """
            {
              "testEnum" : "VAL1"
            }""".trimIndent()
        assertThat(fromJson(reader, json)).isEqualTo(expected)
        assertThat(toJson(writer, expected)).isEqualTo(json)
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
        @Throws(IOException::class)
        override fun read(parser: JsonParser): T? {
            return this.reader?.read(parser)
        }

        @Throws(IOException::class)
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
        } catch (e: RuntimeException) {
            null
        }
        return WriterAndReader(writer, reader)
    }

    //
    @Suppress("UNCHECKED_CAST")
    private class JsonClassLoader(private val cl: ClassLoader) {
        fun <T> writer(type: Class<T>, vararg args: Any?): JsonWriter<T?> {
            return try {
                val writerType = loadWriter(type)
                writerType.constructors[0].newInstance(*args) as JsonWriter<T?>
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }

        fun <T> reader(type: Class<T>, vararg args: Any?): JsonReader<T?> {
            return try {
                val readerType = loadReader(type)
                val constructor = readerType.constructors[0]
                constructor.newInstance(*args) as JsonReader<T?>
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }

        fun <T> loadWriter(type: Class<T>): Class<JsonWriter<T>> {
            return try {
                val packageName = type.packageName
                val name = packageName + "." + prefix(type) + type.simpleName + "JsonWriter"
                cl.loadClass(name) as Class<JsonWriter<T>>
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
        }

        fun <T> loadReader(type: Class<T>): Class<JsonReader<T>> {
            return try {
                val packageName = type.packageName
                val name = packageName + "." + prefix(type) + type.simpleName + "JsonReader"
                cl.loadClass(name) as Class<JsonReader<T>>
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
        }

        private fun prefix(type: Class<*>): StringBuilder {
            val name = StringBuilder("$")
            var parent = type.declaringClass
            while (parent != null) {
                name.insert(1, parent.simpleName + "_")
                parent = parent.declaringClass
            }
            return name
        }

    }


}
