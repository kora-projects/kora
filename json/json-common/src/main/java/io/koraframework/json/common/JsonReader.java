package io.koraframework.json.common;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.annotation.Mapping;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;

import java.io.InputStream;

/**
 * <b>Русский</b>: Контракт читателя JSON со всеми методами чтения
 * <hr>
 * <b>English</b>: JSON reader contract with all read methods
 */
public interface JsonReader<T> extends Mapping.MappingFunction {

    @Nullable
    T read(tools.jackson.core.JsonParser parser) throws JacksonException;

    @Nullable
    default T read(byte[] bytes) throws JacksonException {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), bytes)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(byte[] bytes, int offset, int length) throws JacksonException {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), bytes, offset, length)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(String str) throws JacksonException {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), str)) {
            parser.nextToken();
            return this.read(parser);
        }
    }

    @Nullable
    default T read(InputStream is) throws JacksonException {
        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), is)) {
            parser.nextToken();
            return this.read(parser);
        }
    }
}
