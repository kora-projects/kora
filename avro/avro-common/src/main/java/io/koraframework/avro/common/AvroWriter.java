package io.koraframework.avro.common;

import org.apache.avro.generic.IndexedRecord;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * <b>Русский</b>: Контракт писателя AVRO со всеми методами записи
 * <hr>
 * <b>English</b>: AVRO writer contract with all write methods
 */
public interface AvroWriter<T extends IndexedRecord> {

    byte[] writeBytes(T value) throws IOException;

    default byte[] writeBytesUnchecked(T value) throws UncheckedIOException {
        try {
            return writeBytes(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
