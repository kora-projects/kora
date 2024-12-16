package ru.tinkoff.kora.avro.annotation.processor;

import org.apache.avro.generic.IndexedRecord;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroJsonTests extends AbstractAvroAnnotationProcessorTest {

    @Test
    public void testReaderFromExtension() {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@AvroJson AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var reader = readerJson("TestAvro");
        assertThat(reader).isNotNull();

        var testAvro = getTestAvroGenerated();
        byte[] bytes = getTestAvroAsJson().getBytes(StandardCharsets.UTF_8);
        IndexedRecord read = reader.readUnchecked(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testWriterFromExtension() {
        compile(List.of(new KoraAppProcessor()),
            getAvroClass(),
            """
                @KoraApp
                public interface TestApp {
                  @Root
                  default String root(@AvroJson AvroWriter<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        var writer = writerJson("TestAvro");
        assertThat(writer).isNotNull();

        IndexedRecord testAvro = getTestAvroGenerated();
        byte[] bytes = writer.writeBytesUnchecked(testAvro);
        IndexedRecord restored = readAsJson(bytes);
        assertThatTestAvroValid(testAvro, restored);
    }
}
