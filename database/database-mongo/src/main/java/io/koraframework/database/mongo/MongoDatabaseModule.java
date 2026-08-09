package io.koraframework.database.mongo;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.database.common.DatabaseModule;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DocumentCodec;

public interface MongoDatabaseModule extends DatabaseModule {

    @FactoryModule
    default MongoDatabaseFactoryModule mongoDatabase() {
        return new MongoDatabaseFactoryModule("mongo");
    }

    /**
     * <b>Русский</b>: Кодек для работы с документом без сущности, например в запросах со свободной схемой.
     * <hr>
     * <b>English</b>: Codec for working with a document without an entity, for example in schema-free queries.
     */
    @DefaultComponent
    default Codec<Document> documentMongoCodec() {
        return new DocumentCodec();
    }

    @DefaultComponent
    default Codec<BsonDocument> bsonDocumentMongoCodec() {
        return new BsonDocumentCodec();
    }
}
