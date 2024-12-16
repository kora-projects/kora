package ru.tinkoff.kora.avro.common;

import jakarta.annotation.Nullable;
import org.apache.avro.generic.IndexedRecord;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * <b>Русский</b>: Контракт писателя AVRO со всеми методами записи
 * <hr>
 * <b>English</b>: AVRO writer contract with all write methods
 */
public interface AvroWriter<T extends IndexedRecord> {

    byte[] writeBytes(@Nullable T value) throws IOException;

    default byte[] writeBytesUnchecked(@Nullable T value) throws UncheckedIOException {
        try {
            return writeBytes(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
