package io.koraframework.database.mongo;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * <b>Русский</b>: Вспомогательные методы кодирования значений в BSON, используются сгенерированным кодом репозиториев.
 * <hr>
 * <b>English</b>: Helpers that encode values into BSON, used by generated repository code.
 */
public final class MongoValues {

    private static final String FIELD = "v";

    private MongoValues() {}

    /**
     * <b>Русский</b>: Кодирует значение указанным кодеком в {@link BsonValue}.
     * <hr>
     * <b>English</b>: Encodes a value into a {@link BsonValue} with the given codec.
     */
    public static <T> BsonValue encode(Codec<T> codec, @Nullable T value) {
        if (value == null) {
            return BsonNull.VALUE;
        }

        var document = new BsonDocument();
        try (var writer = new BsonDocumentWriter(document)) {
            writer.writeStartDocument();
            writer.writeName(FIELD);
            codec.encode(writer, value, EncoderContext.builder().build());
            writer.writeEndDocument();
        }
        return document.get(FIELD);
    }

    /**
     * <b>Русский</b>: Кодирует коллекцию значений указанным кодеком в {@link BsonArray}.
     * <hr>
     * <b>English</b>: Encodes a collection of values into a {@link BsonArray} with the given codec.
     */
    public static <T> BsonValue encodeAll(Codec<T> codec, @Nullable Collection<? extends T> values) {
        if (values == null) {
            return BsonNull.VALUE;
        }

        var array = new BsonArray(values.size());
        for (var value : values) {
            array.add(encode(codec, value));
        }
        return array;
    }
}
