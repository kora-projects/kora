package io.koraframework.database.mongo;

import io.koraframework.database.common.annotation.Column;
import io.koraframework.database.common.annotation.Id;
import io.koraframework.database.mongo.annotation.EntityMongo;
import io.koraframework.database.mongo.annotation.MongoCollection;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@EntityMongo
@MongoCollection("users")
public record TestUser(@Id ObjectId id,
                      String login,
                      int age,
                      @Column("created_at") Instant createdAt,
                      List<String> tags,
                      @Nullable String comment) {
}
