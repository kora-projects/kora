package io.koraframework.avro.annotation.processor;

import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import io.koraframework.avro.common.AvroReader;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroTests extends AbstractAvroAnnotationProcessorTest {

    @Test
    public void testReaderFromExtension() throws IOException {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                import io.koraframework.avro.common.AvroReader;@KoraApp
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
        IndexedRecord read = reader.read(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    public void testReaderTaggedFromExtension() throws IOException {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                import io.koraframework.avro.common.AvroReader;@KoraApp
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
        IndexedRecord read = reader.read(bytes);
        assertThatTestAvroValid(testAvro, read);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReaderResolvesEvolvedWriterSchema() throws IOException {
        compile(List.of(new AvroAnnotationProcessor(), new KoraAppProcessor()),
            """
                import io.koraframework.avro.common.AvroReader;@KoraApp
                public interface TestApp {
                  @Root
                  default String root(AvroReader<TestAvro> r) {return "";}
                }
                """);

        compileResult.assertSuccess();
        AvroReader<IndexedRecord> reader = reader("iokoraframework.kora.avro", "TestAvro");
        Schema readerSchema = reader.getSchema();
        assertThat(readerSchema).isNotNull();

        // Writer schema = reader schema + an extra trailing optional field (a backward-compatible evolution).
        // A reader that ignores the schema id would decode this incorrectly; resolution must drop the extra field.
        var fields = new ArrayList<Schema.Field>();
        for (var f : readerSchema.getFields()) {
            fields.add(new Schema.Field(f.name(), f.schema(), f.doc(), f.hasDefaultValue() ? f.defaultVal() : null));
        }
        fields.add(new Schema.Field("extra",
            Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING)),
            null, JsonProperties.NULL_VALUE));
        var writerSchema = Schema.createRecord(readerSchema.getName(), readerSchema.getDoc(), readerSchema.getNamespace(), false, fields);
        assertThat(writerSchema).isNotEqualTo(readerSchema);

        var record = new GenericData.Record(writerSchema);
        record.put("cluster", "cluster");
        record.put("date", 0L);
        record.put("description", "descr");
        record.put("counter", 12345L);
        record.put("flag", true);
        record.put("extra", "ignored-by-reader");

        byte[] bytes;
        try (var out = new ByteArrayOutputStream()) {
            var encoder = EncoderFactory.get().directBinaryEncoder(out, null);
            new GenericDatumWriter<GenericRecord>(writerSchema).write(record, encoder);
            encoder.flush();
            bytes = out.toByteArray();
        }

        IndexedRecord read = reader.read(writerSchema, bytes);
        assertThatTestAvroValid(getTestAvroGeneratedRecord(), read);
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
