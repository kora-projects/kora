package ru.tinkoff.kora.avro.annotation.processor;

import org.apache.avro.generic.IndexedRecord;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroBinaryTests extends AbstractAvroAnnotationProcessorTest {

    @Test
    public void testReaderFromExtension() {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var reader = readerBinary("TestAvro");
        assertThat(reader).isNotNull();

        var testAvro = getTestAvroGenerated();
        byte[] bytes = getTestAvroAsBytes();
        IndexedRecord read = reader.readUnchecked(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testReaderBinaryFromExtension() {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@AvroBinary AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var reader = readerBinary("TestAvro");
        assertThat(reader).isNotNull();

        var testAvro = getTestAvroGenerated();
        byte[] bytes = getTestAvroAsBytes();
        IndexedRecord read = reader.readUnchecked(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testWriterFromExtension() throws IOException {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(AvroWriter<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var writer = writerBinary("TestAvro");
        assertThat(writer).isNotNull();

        IndexedRecord testAvro = getTestAvroGenerated();
        byte[] bytes = writer.writeBytesUnchecked(testAvro);
        IndexedRecord restored = readAsBinary(bytes);
        assertThatTestAvroValid(testAvro, restored);
    }

    @Test
    public void testWriterBinaryFromExtension() {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@AvroBinary AvroWriter<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var writer = writerBinary("TestAvro");
        assertThat(writer).isNotNull();

        IndexedRecord testAvro = getTestAvroGenerated();
        byte[] bytes = writer.writeBytesUnchecked(testAvro);
        IndexedRecord restored = readAsBinary(bytes);
        assertThatTestAvroValid(testAvro, restored);
    }
}
