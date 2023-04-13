package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
public class GenericsTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testRecordWithTypeParameter() {
        compile("""
            @Json
            public record TestRecord <T>(T value, java.util.List<T> values) {
            }
            """);
        var r1 = Mockito.mock(JsonReader.class);
        var r2 = Mockito.mock(JsonReader.class);
        var w1 = Mockito.mock(JsonWriter.class);
        var w2 = Mockito.mock(JsonWriter.class);
        var m1 = mapper("TestRecord", List.of(r1, r2), List.of(w1, w2));
    }

    @Test
    public void testRecordWithTypeParametersFromExtension() throws IOException, ClassNotFoundException {
        compile(List.of(new JsonAnnotationProcessor(), new KoraAppProcessor()), """
                @Json
                public record TestRecord <T>(T value, java.util.List<T> values) {
                }
                """,
            """
                @KoraApp
                public interface TestApp extends ru.tinkoff.kora.json.common.JsonCommonModule {
                    @Root
                    default String root1(ru.tinkoff.kora.json.common.JsonWriter<TestRecord<Integer>> w, ru.tinkoff.kora.json.common.JsonReader<TestRecord<Integer>> r) { return ""; }
                    @Root
                    default String root2(ru.tinkoff.kora.json.common.JsonWriter<TestRecord<String>> w, ru.tinkoff.kora.json.common.JsonReader<TestRecord<String>> r) { return ""; }
                }
                    """);
        compileResult.assertSuccess();
        var graph = loadGraph("TestApp");
        var reader = (JsonReader) graph.findByType(compileResult.loadClass("$TestRecordJsonReader"));
        var writer = (JsonWriter) graph.findByType(compileResult.loadClass("$TestRecordJsonWriter"));

        var o = newObject("TestRecord", 42, List.of(42, 43));
        var json = "{\"value\":42,\"values\":[42,43]}";

        assertThat(reader.read(json.getBytes(StandardCharsets.UTF_8))).isEqualTo(o);
        assertThat(writer.toByteArray(o)).asString(StandardCharsets.UTF_8).isEqualTo(json);
    }

    @Test
    public void testRecordWithTypeParametersFromExtensionNoAnnotations() throws IOException, ClassNotFoundException {
        compile(List.of(new JsonAnnotationProcessor(), new KoraAppProcessor()), """
                public record TestRecord <T>(T value, java.util.List<T> values) {
                }
                """,
            """
                @KoraApp
                public interface TestApp extends ru.tinkoff.kora.json.common.JsonCommonModule {
                    @Root
                    default String root1(ru.tinkoff.kora.json.common.JsonWriter<TestRecord<Integer>> w, ru.tinkoff.kora.json.common.JsonReader<TestRecord<Integer>> r) { return ""; }
                    @Root
                    default String root2(ru.tinkoff.kora.json.common.JsonWriter<TestRecord<String>> w, ru.tinkoff.kora.json.common.JsonReader<TestRecord<String>> r) { return ""; }
                }
                    """);
        compileResult.assertSuccess();
        var graph = loadGraph("TestApp");
        var reader = (JsonReader) graph.findByType(compileResult.loadClass("$TestRecordJsonReader"));
        var writer = (JsonWriter) graph.findByType(compileResult.loadClass("$TestRecordJsonWriter"));

        var o = newObject("TestRecord", 42, List.of(42, 43));
        var json = "{\"value\":42,\"values\":[42,43]}";

        assertThat(reader.read(json.getBytes(StandardCharsets.UTF_8))).isEqualTo(o);
        assertThat(writer.toByteArray(o)).asString(StandardCharsets.UTF_8).isEqualTo(json);
    }
}
