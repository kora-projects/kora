package ru.tinkoff.kora.avro.annotation.processor;

import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.*;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.avro.common.AvroWriter;

import java.io.*;
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
            import ru.tinkoff.kora.common.KoraApp;
            import ru.tinkoff.kora.avro.common.annotation.*;
            import ru.tinkoff.kora.avro.common.AvroReader;
            import ru.tinkoff.kora.avro.common.AvroWriter;
            import java.util.Optional;
            """;
    }

    protected IndexedRecord getTestAvroGenerated() {
        IndexedRecord testAvro = (IndexedRecord) newGeneratedObject("TestAvro").get();
        testAvro.put(0, "cluster");
        testAvro.put(1, Instant.EPOCH);
        testAvro.put(2, "descr");
        testAvro.put(3, 12345L);
        testAvro.put(4, true);
        return testAvro;
    }

    protected byte[] getTestAvroAsBytes() {
        return Base64.getDecoder().decode("DmNsdXN0ZXICAAIKZGVzY3IC8sABAgE=");
    }

    protected String getTestAvroAsJson() {
        return "{\"cluster\":\"cluster\",\"date\":{\"long\":0},\"description\":{\"string\":\"descr\"},\"counter\":{\"long\":12345},\"flag\":{\"boolean\":true}}";
    }

    protected void assertThatTestAvroValid(IndexedRecord expected, IndexedRecord actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.get(0).toString()).isEqualTo(actual.get(0).toString());
        assertThat(actual.get(1)).isEqualTo(expected.get(1));
        assertThat(actual.get(2).toString()).isEqualTo(actual.get(2).toString());
        assertThat(actual.get(3)).isEqualTo(expected.get(3));
        assertThat(actual.get(4)).isEqualTo(expected.get(4));
    }

    protected String getAvroClass() {
        try {
            List<String> strings = Files.lines(new File("build/generated/sources/avro/tinkoff/kora/TestAvro.java").toPath())
                .map(s -> s.replace("tinkoff.kora.", ""))
                .toList();
            String avro = String.join("\n", strings.subList(7, strings.size()));
            return avro.replace("tinkoff.kora", testPackage());
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
    protected <T extends IndexedRecord> AvroReader<T> readerBinary(Class<T> forClass, Object... params) {
        try {
            return (AvroReader<T>) this.compileResult.loadClass("$" + forClass + "_AvroBinaryReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroReader<T> readerBinary(String forClass, Object... params) {
        try {
            return (AvroReader<T>) this.compileResult.loadClass("$" + forClass + "_AvroBinaryReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroReader<T> readerJson(String forClass, Object... params) {
        try {
            return (AvroReader<T>) this.compileResult.loadClass("$" + forClass + "_AvroJsonReader")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroWriter<T> writerBinary(String forClass, Object... params) {
        try {
            return (AvroWriter<T>) this.compileResult.loadClass("$" + forClass + "_AvroBinaryWriter")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends IndexedRecord> AvroWriter<T> writerJson(String forClass, Object... params) {
        try {
            return (AvroWriter<T>) this.compileResult.loadClass("$" + forClass + "_AvroJsonWriter")
                .getConstructors()[0]
                .newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T extends IndexedRecord> ReaderAndWriter<T> mapper(String forClass) {
        return mapper(forClass, List.of(), List.of());
    }

    protected <T extends IndexedRecord> ReaderAndWriter<T> mapper(String forClass, List<?> readerParams, List<?> writerParams) {
        AvroReader<T> reader = readerBinary(forClass, readerParams.toArray());
        AvroWriter<T> writer = writerBinary(forClass, writerParams.toArray());
        return new ReaderAndWriter<T>(reader, writer);
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
                var Avro = this.writer.writeBytes(expectedObject);
                assertThat(Avro).asString(StandardCharsets.UTF_8).isEqualTo(expectedAvro);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // json
    protected byte[] writeAsJson(IndexedRecord value) {
        try (var stream = new ByteArrayOutputStream()) {
            var writer = new SpecificDatumWriter<>(value.getSchema());
            Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(value.getSchema(), stream);
            writer.write(value, jsonEncoder);
            jsonEncoder.flush();
            return stream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // header
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

    // fields
    protected IndexedRecord readAsBinary(byte[] value) {
        try {
            Field fieldData = getTestAvroGenerated().getClass().getDeclaredField("MODEL$");
            fieldData.setAccessible(true);
            SpecificData data = (SpecificData) fieldData.get(value);

            var reader = new SpecificDatumReader<>(getTestAvroGenerated().getSchema(), getTestAvroGenerated().getSchema(), data);
            var binaryDecoder = DecoderFactory.get().binaryDecoder(value, null);
            return (IndexedRecord) reader.read(null, binaryDecoder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected IndexedRecord readAsJson(byte[] value) {
        try {
            Field fieldData = getTestAvroGenerated().getClass().getDeclaredField("MODEL$");
            fieldData.setAccessible(true);
            SpecificData data = (SpecificData) fieldData.get(value);

            var reader = new SpecificDatumReader<>(getTestAvroGenerated().getSchema(), getTestAvroGenerated().getSchema(), data);
            var binaryDecoder = DecoderFactory.get().jsonDecoder(getTestAvroGenerated().getSchema(), new ByteArrayInputStream(value));
            return (IndexedRecord) reader.read(null, binaryDecoder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
