package io.koraframework.avro.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.avro.common.AvroReader;
import io.koraframework.avro.common.AvroWriter;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractAvroAnnotationProcessorTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.common.annotation.KoraApp;
            import io.koraframework.avro.common.annotation.*;
            import io.koraframework.avro.common.AvroReader;
            import io.koraframework.avro.common.AvroWriter;
            import iokoraframework.kora.avro.TestAvro;
            import java.util.Optional;
            """;
    }

    protected IndexedRecord getTestAvroGenerated() {
        return getTestAvroGeneratedRecord();
    }

    protected IndexedRecord getTestAvroGeneratedRecord() {
        return iokoraframework.kora.avro.TestAvro.newBuilder()
            .setCluster("cluster")
            .setDate(Instant.EPOCH)
            .setDescription("descr")
            .setCounter(12345L)
            .setFlag(true)
            .build();
    }

    protected byte[] getTestAvroAsBytes() {
        return Base64.getDecoder().decode("DmNsdXN0ZXICAAIKZGVzY3IC8sABAgE=");
    }

    protected void assertThatTestAvroValid(IndexedRecord expected, IndexedRecord actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.get(0).toString()).isEqualTo(expected.get(0).toString());
        assertThat(actual.get(1)).isEqualTo(expected.get(1));
        assertThat(actual.get(2).toString()).isEqualTo(expected.get(2).toString());
        assertThat(actual.get(3)).isEqualTo(expected.get(3));
        assertThat(actual.get(4)).isEqualTo(expected.get(4));
    }

    protected String getAvroClass() {
        return getAvroClass(null);
    }

    protected String getAvroClass(@Nullable String annotation) {
        try {
            var path = "build/generated/sources/avro/iokoraframework/kora/avro/TestAvro.java";
            List<String> strings = Files.lines(new File(path).toPath())
                .map(s -> s.replace("iokoraframework.kora.", ""))
                .toList();
            String avro = String.join("\n", strings.subList(7, strings.size()));
            avro = avro.replace("iokoraframework.kora", testPackage());
            if (annotation != null) {
                avro = avro.replaceFirst("@org\\.apache\\.avro\\.specific\\.AvroGenerated", annotation + "\n@org.apache.avro.specific.AvroGenerated");
            }
            return avro;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void compile(@Language("java") String... sources) {
        var compileResult = compile(List.of(new AvroAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroReader<T> reader(Class<T> forClass, Object... params) {
        try {
            return (AvroReader<T>) this.compileResult.loadClass("$" + forClass + "_AvroReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T extends IndexedRecord> AvroReader<T> reader(String forClass, Object... params) {
        return reader(testPackage(), forClass, params);
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroReader<T> reader(String packageName, String forClass, Object... params) {
        try {
            return (AvroReader<T>) this.compileResult.cl().loadClass(packageName + ".$" + forClass + "_AvroReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T extends IndexedRecord> AvroWriter<T> writer(String forClass, Object... params) {
        return writer(testPackage(), forClass, params);
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroWriter<T> writer(String packageName, String forClass, Object... params) {
        try {
            return (AvroWriter<T>) this.compileResult.cl().loadClass(packageName + ".$" + forClass + "_AvroWriter")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T extends IndexedRecord> ReaderAndWriter<T> mapper(String forClass) {
        return mapper(forClass, List.of(), List.of());
    }

    protected <T extends IndexedRecord> ReaderAndWriter<T> mapper(String forClass, List<?> readerParams, List<?> writerParams) {
        AvroReader<T> reader = reader(forClass, readerParams.toArray());
        AvroWriter<T> writer = writer(forClass, writerParams.toArray());
        return new ReaderAndWriter<>(reader, writer);
    }

    protected static class ReaderAndWriter<T extends IndexedRecord> implements AvroReader<T>, AvroWriter<T> {
        private final AvroReader<T> reader;
        private final AvroWriter<T> writer;

        protected ReaderAndWriter(AvroReader<T> reader, AvroWriter<T> writer) {
            this.reader = reader;
            this.writer = writer;
        }

        @Nullable
        @Override
        public T read(ByteBuffer buffer) throws IOException {
            return reader.read(buffer);
        }

        @Nullable
        @Override
        public T read(byte[] bytes) throws IOException {
            return reader.read(bytes);
        }

        @Nullable
        @Override
        public T read(byte[] bytes, int offset, int length) throws IOException {
            return reader.read(bytes, offset, length);
        }

        @Nullable
        @Override
        public T read(InputStream is) throws IOException {
            return reader.read(is);
        }

        @Override
        public byte[] writeBytes(@Nullable T value) throws IOException {
            return writer.writeBytes(value);
        }

        public void verify(T expectedObject, String expectedAvro) {
            verifyRead(expectedAvro, expectedObject);
            verifyWrite(expectedObject, expectedAvro);
        }

        public void verifyRead(String expectedAvro, T expectedObject) {
            try {
                var object = this.reader.read(expectedAvro.getBytes(StandardCharsets.UTF_8));
                assertThat(object).isEqualTo(expectedObject);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void verifyWrite(T expectedObject, String expectedAvro) {
            try {
                var avro = this.writer.writeBytes(expectedObject);
                assertThat(avro).asString(StandardCharsets.UTF_8).isEqualTo(expectedAvro);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected byte[] writeAsBinary(IndexedRecord value) {
        try (var stream = new ByteArrayOutputStream()) {
            Field fieldData = value.getClass().getDeclaredField("MODEL$");
            fieldData.setAccessible(true);
            SpecificData data = (SpecificData) fieldData.get(value);

            var writer = new SpecificDatumWriter<>(value.getSchema(), data);
            Encoder encoder = EncoderFactory.get().directBinaryEncoder(stream, null);
            writer.write(value, encoder);
            encoder.flush();
            return stream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected IndexedRecord readAsBinary(byte[] value) {
        try {
            var generated = getTestAvroGeneratedRecord();
            Field fieldData = generated.getClass().getDeclaredField("MODEL$");
            fieldData.setAccessible(true);
            SpecificData data = (SpecificData) fieldData.get(generated);

            var reader = new SpecificDatumReader<>(generated.getSchema(), generated.getSchema(), data);
            var binaryDecoder = DecoderFactory.get().binaryDecoder(value, null);
            return (IndexedRecord) reader.read(null, binaryDecoder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
