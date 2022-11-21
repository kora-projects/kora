package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class that defines public API for reading JSON content.
 */
public interface JsonReader<T> {

    /**
     * @param parser jackson parser used for deserializing JSON
     * @return deserialized object
     * @throws IOException in case of deserialization errors
     */
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
    default T read(InputStream is) throws IOException {
        try (var parser = JsonCommonModule.JSON_FACTORY.createParser(is)) {
            parser.nextToken();
            return this.read(parser);
        }
    }
}
