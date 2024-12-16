package ru.tinkoff.kora.avro.common;

import jakarta.annotation.Nullable;
import org.apache.avro.generic.IndexedRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * <b>Русский</b>: Контракт читателя AVRO со всеми методами чтения
 * <hr>
 * <b>English</b>: AVRO reader contract with all read methods
 */
public interface AvroReader<T extends IndexedRecord> {

    @Nullable
    T read(InputStream is) throws IOException;

    @Nullable
    default T read(byte[] bytes) throws IOException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return read(is);
        }
    }

    @Nullable
    default T read(byte[] bytes, int offset, int length) throws IOException {
        try (var is = new ByteArrayInputStream(bytes, offset, length)) {
            return read(is);
        }
    }

    @Nullable
    default T read(ByteBuffer buffer) throws IOException {
        if (buffer.hasArray()) {
            try (var is = new ByteArrayInputStream(buffer.array())) {
                return read(is);
            }
        } else {
            try (var is = new ByteBufferInputStream(buffer)) {
                return read(is);
            }
        }
    }

    @Nullable
    default T readUnchecked(byte[] bytes) throws UncheckedIOException {
        try {
            return read(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T readUnchecked(byte[] bytes, int offset, int length) throws UncheckedIOException {
        try {
            return read(bytes, offset, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T readUnchecked(InputStream is) throws UncheckedIOException {
        try {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
