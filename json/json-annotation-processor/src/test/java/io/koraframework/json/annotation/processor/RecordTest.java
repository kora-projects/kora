package io.koraframework.json.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

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
    public void testAnnotationProcessedReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @Json
              record TestRecord(int value){}
            
              @Root
              default String root(io.koraframework.json.common.JsonReader<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestRecord")).isNotNull();
    }

    @Test
    public void testAnnotationProcessedWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @Json
              record TestRecord(int value){}
            
              @Root
              default String root(io.koraframework.json.common.JsonWriter<TestRecord> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestRecord")).isNotNull();
    }
}
