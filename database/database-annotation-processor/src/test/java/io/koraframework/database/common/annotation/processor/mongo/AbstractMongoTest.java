package io.koraframework.database.common.annotation.processor.mongo;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

abstract class AbstractMongoTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.database.common.annotation.Column;
            import io.koraframework.database.common.annotation.Id;
            import io.koraframework.database.mongo.annotation.EntityMongo;
            import io.koraframework.database.mongo.annotation.MongoCollection;
            import org.bson.types.ObjectId;
            import org.jspecify.annotations.Nullable;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            """;
    }

    @SuppressWarnings("unchecked")
    protected Codec<Object> codec(String className, Object... params) {
        return (Codec<Object>) newObject(className, params);
    }

    protected BsonDocument encode(Codec<Object> codec, Object value) {
        var document = new BsonDocument();
        try (var writer = new BsonDocumentWriter(document)) {
            codec.encode(writer, value, EncoderContext.builder().build());
        }
        return document;
    }

    protected Object decode(Codec<Object> codec, BsonDocument document) {
        try (var reader = new BsonDocumentReader(document)) {
            return codec.decode(reader, DecoderContext.builder().build());
        }
    }

    protected Object roundTrip(Codec<Object> codec, Object value) {
        return decode(codec, encode(codec, value));
    }
}
