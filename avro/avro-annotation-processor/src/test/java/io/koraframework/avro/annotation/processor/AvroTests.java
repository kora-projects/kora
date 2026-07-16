package io.koraframework.avro.annotation.processor;

import org.apache.avro.generic.IndexedRecord;
import org.junit.jupiter.api.Test;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroTests extends AbstractAvroAnnotationProcessorTest {

    @Test
    public void testReaderFromExtension() {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var reader = reader("iokoraframework.kora.avro", "TestAvro");
        assertThat(reader).isNotNull();

        var testAvro = getTestAvroGeneratedRecord();
        byte[] bytes = getTestAvroAsBytes();
        IndexedRecord read = reader.readUnchecked(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testReaderTaggedFromExtension() {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@Avro AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var reader = reader("iokoraframework.kora.avro", "TestAvro");
        assertThat(reader).isNotNull();

        var testAvro = getTestAvroGeneratedRecord();
        byte[] bytes = getTestAvroAsBytes();
        IndexedRecord read = reader.readUnchecked(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testWriterFromExtension() throws IOException {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(AvroWriter<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var writer = writer("iokoraframework.kora.avro", "TestAvro");
        assertThat(writer).isNotNull();

        IndexedRecord testAvro = getTestAvroGeneratedRecord();
        byte[] bytes = writer.writeBytesUnchecked(testAvro);
        IndexedRecord restored = readAsBinary(bytes);
        assertThatTestAvroValid(testAvro, restored);
    }

    @Test
    public void testWriterTaggedFromExtension() {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@Avro AvroWriter<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var writer = writer("iokoraframework.kora.avro", "TestAvro");
        assertThat(writer).isNotNull();

        IndexedRecord testAvro = getTestAvroGeneratedRecord();
        byte[] bytes = writer.writeBytesUnchecked(testAvro);
        IndexedRecord restored = readAsBinary(bytes);
        assertThatTestAvroValid(testAvro, restored);
    }
}
