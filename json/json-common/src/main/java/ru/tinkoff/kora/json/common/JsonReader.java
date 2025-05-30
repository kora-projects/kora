package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParser;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * <b>Русский</b>: Контракт читателя JSON со всеми методами чтения
 * <hr>
 * <b>English</b>: JSON reader contract with all read methods
 */
public interface JsonReader<T> {

    @Nullable
    T read(JsonParser parser) throws IOException;

    @Nullable
    default T read(byte[] bytes) throws IOException {
        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(bytes)) {
            parser.nextToken();
            return this.read(parser);
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
    default T read(byte[] bytes, int offset, int length) throws IOException {
        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(bytes, offset, length)) {
            parser.nextToken();
            return this.read(parser);
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
    default T read(String str) throws IOException {
        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(str)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T readUnchecked(String str) throws UncheckedIOException {
        try {
            return read(str);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    default T read(InputStream is) throws IOException {
        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(is)) {
            parser.nextToken();
            return this.read(parser);
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
