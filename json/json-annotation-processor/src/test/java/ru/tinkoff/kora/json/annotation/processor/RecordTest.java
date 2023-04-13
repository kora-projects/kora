package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testRecord() {
        compile("""
            @Json
            public record TestRecord(int value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42), "{\"value\":42}");
    }


    @Test
    public void testReaderFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              record TestRecord(int value){}
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestRecord")).isNotNull();
    }

    @Test
    public void testWriterFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              record TestRecord(int value){}
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestRecord")).isNotNull();
    }

    @Test
    public void testAnnotationProcessedReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @Json
              record TestRecord(int value){}
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestRecord")).isNotNull();
    }

    @Test
    public void testAnnotationProcessedWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @Json
              record TestRecord(int value){}
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestRecord")).isNotNull();
    }
}
