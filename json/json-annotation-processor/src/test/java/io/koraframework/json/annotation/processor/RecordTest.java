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
    public void testDuplicateKeysLastWins() {
        compile("""
            @Json
            public record TestRecord(int a, int b) {
            }
            """);

        var reader = reader("TestRecord");
        assertThat(reader.read("{\"a\":1,\"b\":2,\"a\":3}")).isEqualTo(newObject("TestRecord", 3, 2));
        assertThat(reader.read("{\"b\":2,\"a\":1,\"a\":3}")).isEqualTo(newObject("TestRecord", 3, 2));
        assertThat(reader.read("{\"a\":1,\"b\":2,\"c\":{\"a\":5},\"b\":4}")).isEqualTo(newObject("TestRecord", 1, 4));
    }

    @Test
    public void testAnnotationProcessedReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
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
            @io.koraframework.common.annotation.KoraApp
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
