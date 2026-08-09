package io.koraframework.database.mongo;

import io.koraframework.database.common.UpdateCount;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.mongo.annotation.MongoAggregate;
import io.koraframework.database.mongo.annotation.MongoCollection;
import io.koraframework.database.mongo.annotation.MongoCount;
import io.koraframework.database.mongo.annotation.MongoDelete;
import io.koraframework.database.mongo.annotation.MongoFind;
import io.koraframework.database.mongo.annotation.MongoInsert;
import io.koraframework.database.mongo.annotation.MongoReplace;
import io.koraframework.database.mongo.annotation.MongoUpdate;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

@Repository
@MongoCollection("users")
public interface TestUserRepository extends MongoRepository {

    @MongoInsert
    void insert(TestUser user);

    @MongoInsert
    void insertAll(List<TestUser> users);

    @MongoFind(filter = "{\"_id\": :id}")
    Optional<TestUser> findById(ObjectId id);

    @MongoFind(filter = "{\"login\": :login}")
    TestUser findByLogin(String login);

    @MongoFind(filter = "{}", sort = "{\"age\": -1}", limit = "2")
    List<TestUser> findTwoOldest();

    @MongoFind(filter = "{}", sort = "{\"age\": -1}", limit = ":size", skip = ":offset")
    List<TestUser> findPage(int size, int offset);

    @MongoFind(filter = "{\"login\": {\"$in\": :logins}}")
    List<TestUser> findByLogins(List<String> logins);

    @MongoFind(filter = "{\"age\": {\"$gte\": :minAge}}")
    List<TestUser> findOlderThan(int minAge);

    @MongoCount(filter = "{}")
    long countAll();

    @MongoUpdate(filter = "{\"_id\": :id}", update = "{\"$set\": {\"login\": :login}}")
    UpdateCount rename(ObjectId id, String login);

    @MongoUpdate(filter = "{}", update = "{\"$inc\": {\"age\": 1}}", many = true)
    UpdateCount ageEveryone();

    @MongoReplace(filter = "{\"_id\": :id}")
    UpdateCount replace(ObjectId id, TestUser user);

    @MongoDelete(filter = "{\"_id\": :id}")
    UpdateCount deleteById(ObjectId id);

    @MongoDelete(filter = "{}", many = true)
    UpdateCount deleteAll();

    @MongoAggregate("[{\"$match\": {\"age\": {\"$gte\": :minAge}}}, {\"$group\": {\"_id\": null, \"total\": {\"$sum\": 1}}}]")
    List<Document> countOlderThan(int minAge);
}
