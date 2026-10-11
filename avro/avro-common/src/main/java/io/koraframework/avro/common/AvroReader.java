package io.koraframework.avro.common;

import io.koraframework.common.util.ByteBufferInputStream;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * <b>Русский</b>: Контракт читателя AVRO со всеми методами чтения. Все методы бросают {@link UncheckedIOException}.
 * <hr>
 * <b>English</b>: AVRO reader contract with all read methods. All methods throw {@link UncheckedIOException}.
 */
public interface AvroReader<T extends IndexedRecord> {

    /**
     * <b>Русский</b>: Схема данного читателя (reader schema).
     * <hr>
     * <b>English</b>: The reader schema of this reader.
     */
    Schema getSchema();

    @Nullable
    T read(InputStream is) throws UncheckedIOException;

    @Nullable
    default T read(byte[] bytes) throws UncheckedIOException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T read(byte[] bytes, int offset, int length) throws UncheckedIOException {
        try (var is = new ByteArrayInputStream(bytes, offset, length)) {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T read(ByteBuffer buffer) throws UncheckedIOException {
        try {
            if (buffer.hasArray()) {
                try (var is = new ByteArrayInputStream(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())) {
                    return read(is);
                }
            } else {
                try (var is = new ByteBufferInputStream(buffer)) {
                    return read(is);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * <b>Русский</b>: Читает запись, выполняя разрешение схем (schema resolution) между схемой писателя и схемой читателя.
     * Используется когда данные записаны другой (эволюционировавшей) версией схемы, например при чтении из Schema Registry.
     * <hr>
     * <b>English</b>: Reads a record performing Avro schema resolution between the writer schema and the reader schema.
     * Use this when the payload was written with a different (evolved) schema, e.g. resolved via a Schema Registry.
     *
     * @param writerSchema the schema the data was written with; when {@code null} the reader schema is assumed
     */
    @Nullable
    T read(@Nullable Schema writerSchema, InputStream is) throws UncheckedIOException;

    @Nullable
    default T read(@Nullable Schema writerSchema, byte[] bytes) throws UncheckedIOException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return read(writerSchema, is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T read(@Nullable Schema writerSchema, byte[] bytes, int offset, int length) throws UncheckedIOException {
        try (var is = new ByteArrayInputStream(bytes, offset, length)) {
            return read(writerSchema, is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T read(@Nullable Schema writerSchema, ByteBuffer buffer) throws UncheckedIOException {
        try {
            if (buffer.hasArray()) {
                try (var is = new ByteArrayInputStream(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())) {
                    return read(writerSchema, is);
                }
            } else {
                try (var is = new ByteBufferInputStream(buffer)) {
                    return read(writerSchema, is);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
