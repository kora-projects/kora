package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.json.annotation.processor.dto.*;
import ru.tinkoff.kora.json.annotation.processor.dto.DtoWithInnerDto.InnerDto;
import ru.tinkoff.kora.json.common.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonAnnotationProcessorTest {

    @Test
    void testReadWriteAllSupportedTypes() throws Exception {
        var cl = processClass0(DtoWithSupportedTypes.class);
        var reader = cl.reader(DtoWithSupportedTypes.class, new ListJsonReader<>(JsonParser::getIntValue), new SetJsonReader<>(JsonParser::getIntValue));
        var writer = cl.writer(DtoWithSupportedTypes.class, new ListJsonWriter<Integer>(JsonGenerator::writeNumber), new SetJsonWriter<Integer>(JsonGenerator::writeNumber));

        var object = new DtoWithSupportedTypes(
            "string", true, false,
            1, -1, BigInteger.TEN, BigDecimal.TEN,
            0.4d, 0.5d, 0.6f, 0.7f,
            100L, 101L, (short) 10, (short) 11,
            new byte[]{1, 2, 3},
            List.of(1), Set.of(1));

        var json = toJson(writer, object);

        var parsed = fromJson(reader, json);
        assertThat(toStringExcludeBinary(parsed)).isEqualTo(toStringExcludeBinary(object));
    }

    @Test
    void testDtoWithTypeParams() throws Exception {
        JsonReader<Integer> intReader = JsonParser::getIntValue;
        JsonReader<String> stringJsonReader = JsonParser::getText;

        var cl = processClass0(DtoWithTypeParam.class);
        var reader = cl.reader(
            DtoWithTypeParam.class,
            cl.reader(DtoWithTypeParam.FirstTpe.class, intReader, stringJsonReader),
            cl.reader(DtoWithTypeParam.SecondTpe.class, intReader),
            cl.reader(DtoWithTypeParam.ThirdTpe.class, stringJsonReader)
        );


        JsonWriter<Integer> intWriter = JsonGenerator::writeNumber;
        JsonWriter<String> stringWriter = JsonGenerator::writeString;
        var writer = cl.writer(
            DtoWithTypeParam.class,
            cl.writer(DtoWithTypeParam.FirstTpe.class, intWriter, stringWriter),
            cl.writer(DtoWithTypeParam.SecondTpe.class, intWriter),
            cl.writer(DtoWithTypeParam.ThirdTpe.class, stringWriter)
        );

        var expected1 = new DtoWithTypeParam.FirstTpe<>(1, "a", 2);
        Assertions.assertEquals(expected1, fromJson(reader, toJson(writer, expected1)));

        var expected2 = new DtoWithTypeParam.SecondTpe<>(1);
        Assertions.assertEquals(expected2, fromJson(reader, toJson(writer, expected2)));

        var expected3 = new DtoWithTypeParam.ThirdTpe<>("a");
        Assertions.assertEquals(expected3, fromJson(reader, toJson(writer, expected3)));
    }

    @Test
    void testNamingStrategy() throws Exception {
        var cl1 = processClass0(DtoWithSnakeCaseNaming.class);
        var reader = cl1.reader(DtoWithSnakeCaseNaming.class);
        var writer = cl1.writer(DtoWithSnakeCaseNaming.class);

        var json = """
            {
              "string_field" : "Test",
              "integer_field" : 5
            }""";

        var dto = new DtoWithSnakeCaseNaming("Test", 5);

        var parsed = fromJson(reader, json);

        assertThat(dto).isEqualTo(parsed);

        assertThat(toJson(writer, dto)).isEqualTo(json);
    }

    private String toStringExcludeBinary(Object o) {
        var str = o.toString();
        var bIndexOf = str.indexOf("=[B");
        if (bIndexOf < 0) {
            return str;
        }
        var lastBIndex = str.indexOf(", ", bIndexOf);
        return str.substring(0, bIndexOf) + str.substring(lastBIndex);

    }

    @Test
    void testReadWriteJsonFieldProperties() throws Exception {
        var writer = processClass(DtoWithJsonFieldWriter.class);

        var json = toJson(writer, new DtoWithJsonFieldWriter("field1", "field2", "field3", "field4"));

        var expectedJson = """
            {
              "renamedField1" : "field1",
              "renamedField2" : "field2",
              "field3" : -1,
              "field4" : -1
            }""";

        assertThat(json).isEqualTo(expectedJson);


        var newJson = """
            {
              "field0": "field0",
               "renamedField1" : "field1",
               "renamedField2" : "field2",
               "field3" : -1,
               "field4" : -1,
               "field5": [[[[{"field": "value"}]]]]
            }
            """;

        var object = fromJson(writer, newJson);
    }

    @Test
    void testWriteJsonSkip() throws Exception {
        var writer = processClass(DtoWithJsonSkip.class);

        var json = toJson(writer, new DtoWithJsonSkip("field1", "field2", "field3", "field4"));

        assertThat(json).isEqualTo("""
            {
              "field1" : "field1",
              "field2" : "field2"
            }""");
    }

    @Test
    void testWriteJsonSkipNullFields() throws Exception {
        var writer = processClass(DtoWithJsonSkip.class);

        var json = toJson(writer, new DtoWithJsonSkip("field1", null, "field3", "field4"));

        assertThat(json).isEqualTo("""
            {
              "field1" : "field1"
            }""");
    }


    @Test
    void testWriteJsonInnerDto() throws Exception {
        var cl = processClass0(DtoWithInnerDto.class);
        var innerReader = cl.reader(DtoWithInnerDto.InnerDto.class);
        var reader = cl.reader(DtoWithInnerDto.class,
            innerReader,
            new ListJsonReader<>(innerReader),
            new MapJsonReader<>(innerReader),
            new ListJsonReader<>(new ListJsonReader<>(innerReader))
        );
        var innerWriter = cl.writer(DtoWithInnerDto.InnerDto.class);
        var writer = cl.writer(DtoWithInnerDto.class,
            innerWriter,
            new ListJsonWriter<>(innerWriter),
            new MapJsonWriter<>(innerWriter),
            new ListJsonWriter<>(new ListJsonWriter<>(innerWriter))
        );
        var object = new DtoWithInnerDto(
            new InnerDto("field1"),
            List.of(
                new InnerDto("field1"),
                new InnerDto("field2")
            ),
            Map.of(
                "test", new InnerDto("field3")
            ),
            List.of(
                List.of(
                    new InnerDto("field5")
                )
            ));

        var json = toJson(writer, object);

        assertThat(json).isEqualTo("""
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
            }""");

        var parsed = fromJson(reader, json);
        assertThat(parsed).isEqualTo(object);
    }

    @Test
    void testOnlyReaderDto() throws Exception {
        var reader = processClass(DtoOnlyReader.class);
        assertThat(reader.writer()).isNull();
        var expected = new DtoOnlyReader("field1", "field2", new DtoOnlyReader.Inner("3"));

        var object = fromJson(reader, """
            {
              "field1" : "field1",
              "renamedField2" : "field2",
              "field3" : "3"
            }""");

        assertThat(object).isEqualTo(expected);
    }

    @Test
    void testOnlyWriterDto() throws Exception {
        var writer = processClass(DtoOnlyWriter.class);
        assertThat(writer.reader()).isNull();
        assertThat(writer.writer()).isNotNull();
        var object = new DtoOnlyWriter("field1", "field2", new DtoOnlyWriter.Inner("3"), "field4");

        var json = toJson(writer, object);

        assertThat(json).isEqualTo("""
            {
              "field1" : "field1",
              "renamedField2" : "field2",
              "field3" : "3"
            }""");
    }

    @Test
    void testWriteDtoJavaBeans() throws Exception {
        var writer = processClass(DtoJavaBean.class);
        assertThat(writer.reader()).isNull();
        var object = new DtoJavaBean("field1", 2);

        var json = toJson(writer, object);

        assertThat(json).isEqualTo("""
            {
              "string_field" : "field1",
              "int_field" : 2
            }""");
    }

    @Test
    void testNullableBeans() throws Exception {
        var reader = processClass(DtoWithNullableFields.class);
        assertThat(reader.writer()).isNull();

        var expected = new DtoWithNullableFields("field1", 4, "field2", null);
        var object = fromJson(reader, """
            {
              "field_1" : "field1",
              "field2" : "field2",
              "field4" : 4
            }""");
        assertThat(object).isEqualTo(expected);

        expected = new DtoWithNullableFields("field1", 4, null, null);
        object = fromJson(reader, """
            {
              "field_1" : "field1",
              "field4" : 4
            }""");
        assertThat(object).isEqualTo(expected);

        expected = new DtoWithNullableFields("field1", 4, null, null);
        object = fromJson(reader, """
            {
              "field_1" : "field1",
              "field2" : null,
              "field4" : 4
            }""");
        assertThat(object).isEqualTo(expected);


        assertThatThrownBy(() -> fromJson(reader, """
            {
              "field2" : "field2"
            }"""))
            .isInstanceOf(JsonParseException.class)
            .hasMessageStartingWith("Some of required json fields were not received: field1(field_1)");

        assertThatThrownBy(() -> fromJson(reader, """
            {
              "field_1" : "field1",
              "field2" : "field2",
              "field4" : null
            }"""))
            .isInstanceOf(JsonParseException.class)
            .hasMessageStartingWith("Expecting [VALUE_NUMBER_INT] token for field 'field4', got VALUE_NULL");
    }

    @Test
    void testEnum() throws Exception {
        var cl = processClass0(DtoWithEnum.class);
        var reader = cl.reader(DtoWithEnum.class, new EnumJsonReader<>(DtoWithEnum.TestEnum.values(), Enum::name));
        var writer = cl.writer(DtoWithEnum.class, cl.writer(DtoWithEnum.TestEnum.class));

        var expected = new DtoWithEnum(DtoWithEnum.TestEnum.VAL1);
        var json = """
            {
              "testEnum" : "VAL1"
            }""";

        assertThat(fromJson(reader, json)).isEqualTo(expected);
        assertThat(toJson(writer, expected)).isEqualTo(json);
    }

    @Test
    void testObject() throws Exception {
        var cl = processClass0(DtoWithObject.class);
        var reader = cl.reader(DtoWithObject.class, (JsonReader<Object>) JsonObjectCodec::parse);
        var writer = cl.writer(DtoWithObject.class, (JsonWriter<Object>) JsonObjectCodec::write);

        var value1 = new DtoWithObject("string");
        var json1 = "{\n  \"value\" : \"string\"\n}";
        assertThat(toJson(writer, value1)).isEqualTo(json1);
        assertThat(fromJson(reader, json1)).isEqualTo(value1);
    }

    <T> String toJson(JsonWriter<T> writer, T object) {
        var jf = new JsonFactory(new JsonFactoryBuilder());
        var sw = new StringWriter();

        try (var gen = jf.createGenerator(sw)) {
            gen.setPrettyPrinter(new DefaultPrettyPrinter());
            writer.write(gen, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

    <T> T fromJson(JsonReader<T> reader, String json) throws IOException {
        var jf = new JsonFactory(new JsonFactoryBuilder());

        try (var parser = jf.createParser(json)) {
            parser.nextToken();
            return reader.read(parser);
        }
    }

    private record WriterAndReader<T>(JsonWriter<T> writer, JsonReader<T> reader) implements JsonWriter<T>, JsonReader<T> {

        @Override
        public T read(JsonParser parser) throws IOException {
            return this.reader.read(parser);
        }

        @Override
        public void write(JsonGenerator gen, @Nullable T object) throws IOException {
            this.writer.write(gen, object);
        }
    }

    <T> WriterAndReader<T> processClass(Class<T> type) throws Exception {
        var cl = processClass0(type);
        JsonWriter<T> writer;
        JsonReader<T> reader;

        try {
            writer = cl.writer(type);
        } catch (Exception e) {
            writer = null;
        }
        try {
            reader = cl.reader(type);
        } catch (RuntimeException e) {
            reader = null;
        }
        return new WriterAndReader<>(writer, reader);
    }

    @SuppressWarnings("unchecked")
    private static class JsonClassLoader {
        private final ClassLoader cl;

        private JsonClassLoader(ClassLoader cl) {
            this.cl = cl;
        }


        <T> JsonWriter<T> writer(Class<T> type, Object... args) {
            try {
                var writerType = loadWriter(type);
                return (JsonWriter<T>) writerType.getConstructors()[0].newInstance(args);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        <T> JsonReader<T> reader(Class<T> type, Object... args) {
            try {
                var readerType = loadReader(type);
                return (JsonReader<T>) readerType.getConstructors()[0].newInstance(args);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        <T> Class<JsonWriter<T>> loadWriter(Class<T> type) {
            try {
                var packageName = type.getPackageName();
                var name = packageName + "." + prefix(type) + type.getSimpleName() + "JsonWriter";
                return (Class<JsonWriter<T>>) this.cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        <T> Class<JsonReader<T>> loadReader(Class<T> type) {
            try {
                var packageName = type.getPackageName();
                var name = packageName + "." + prefix(type) + type.getSimpleName() + "JsonReader";
                return (Class<JsonReader<T>>) this.cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

    }

    JsonClassLoader processClass0(Class<?> type) throws Exception {
        return new JsonClassLoader(TestUtils.annotationProcess(type, new JsonAnnotationProcessor()));
    }

    private static StringBuilder prefix(Class<?> type) {
        var name = new StringBuilder("$");
        var parent = type.getDeclaringClass();
        while (parent != null) {
            name.insert(1, parent.getSimpleName() + "_");
            parent = parent.getDeclaringClass();
        }
        return name;
    }

}
