package io.koraframework.json.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.net.URI;
import java.util.List;

public class NativeTypesTest extends AbstractJsonAnnotationProcessorTest {

    private final JsonReader<URI> uriReader = parser -> URI.create(parser.getString());
    private final JsonWriter<URI> uriWriter = (generator, object) -> generator.writeString(object.toString());

    @Test
    public void testRecordNativeType() {
        compile("""
            @Json
            public record TestRecord(java.net.URI value) { }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord",
            List.of(uriReader),
            List.of(uriWriter));

        mapper.verify(newObject("TestRecord", URI.create("/some")), "{\"value\":\"/some\"}");
    }

    @Test
    public void testRecordNativeTypeReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()),
            """
                @io.koraframework.common.KoraApp
                public interface TestApp {
                
                  record TestRecord(java.net.URI value) { }
                
                  @Root
                  default String root(io.koraframework.json.common.JsonReader<TestRecord> reader) {return "";}
                }
                """);
        compileResult.isFailed();
    }

    @Test
    public void testRecordNativeTypeWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()),
            """
                @io.koraframework.common.KoraApp
                public interface TestApp {
                
                  record TestRecord(java.net.URI value) { }
                
                  @Root
                  default String root(io.koraframework.json.common.JsonWriter<TestRecord> writer) { return ""; }
                }
                """);

        compileResult.isFailed();
    }
}
