package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import tools.jackson.core.ObjectReadContext;

import java.io.InputStream;

/**
 * <b>Русский</b>: Контракт читателя JSON со всеми методами чтения
 * <hr>
 * <b>English</b>: JSON reader contract with all read methods
 */
public interface JsonReader<T> extends Mapping.MappingFunction {

    @Nullable
    T read(tools.jackson.core.JsonParser parser);

    @Nullable
    default T read(byte[] bytes) {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), bytes)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(byte[] bytes, int offset, int length) {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), bytes, offset, length)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(String str) {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), str)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(InputStream is) {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), is)) {
            parser.nextToken();
            return this.read(parser);
        }
    }
}
