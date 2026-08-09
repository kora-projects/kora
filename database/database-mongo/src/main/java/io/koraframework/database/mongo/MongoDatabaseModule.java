package io.koraframework.database.mongo;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.database.common.DatabaseModule;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.UuidCodec;

import java.util.UUID;

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

    /**
     * <b>Русский</b>: Кодек UUID, по умолчанию в виде {@link UuidRepresentation#STANDARD}, то есть BSON binary subtype 4.
     * Чтобы читать данные, записанные в другом представлении, объявите свой {@code Codec<UUID>} как {@code @Component}.
     * <hr>
     * <b>English</b>: UUID codec, {@link UuidRepresentation#STANDARD} by default, which is BSON binary subtype 4.
     * To read data written in another representation, declare your own {@code Codec<UUID>} as a {@code @Component}.
     */
    @DefaultComponent
    default Codec<UUID> uuidMongoCodec() {
        return new UuidCodec(UuidRepresentation.STANDARD);
    }
}
