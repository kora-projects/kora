package io.koraframework.database.common.annotation.processor.mongo;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

public class MongoRepositoryTest extends AbstractMongoRepositoryTest {

    private final Codec<?> codec = Mockito.mock(Codec.class);

    @BeforeEach
    public void resetExecutor() {
        this.executor.reset();
    }

    @Test
    public void testFindByFilter() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"login\\": :login}")
                Optional<TestEntity> findByLogin(String login);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        var result = repository.invoke("findByLogin", "user");

        assertThat(result).isEqualTo(java.util.Optional.empty());
        verify(this.executor.collection).find(new BsonDocument("login", new BsonString("user")));
    }

    @Test
    public void testFindReturnsList() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", sort = "{\\"login\\": -1}", limit = "10", skip = "5")
                List<TestEntity> findAll();
            }
            """, """
            public record TestEntity(String login) {}
            """);

        var result = repository.invoke("findAll");

        assertThat((List<?>) result).isEmpty();
        verify(this.executor.collection).find(new BsonDocument());
        verify(this.executor.findIterable).sort(new BsonDocument("login", new BsonInt32(-1)));
        verify(this.executor.findIterable).skip(5);
        verify(this.executor.findIterable).limit(10);
    }

    @Test
    public void testLimitAndSkipFromParameters() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", limit = ":size", skip = ":offset")
                List<TestEntity> findPage(int size, int offset);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("findPage", 20, 40);

        verify(this.executor.findIterable).limit(20);
        verify(this.executor.findIterable).skip(40);
    }

    @Test
    public void testFindWithNestedFilterAndOperators() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"age\\": {\\"$gt\\": :minAge}, \\"active\\": true}")
                List<TestEntity> findOlderThan(int minAge);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("findOlderThan", 18);

        verify(this.executor.collection).find(new BsonDocument()
            .append("age", new BsonDocument("$gt", new BsonInt32(18)))
            .append("active", org.bson.BsonBoolean.valueOf(true)));
    }

    @Test
    public void testFindWithCollectionParameter() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"login\\": {\\"$in\\": :logins}}")
                List<TestEntity> findByLogins(List<String> logins);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("findByLogins", List.of("a", "b"));

        verify(this.executor.collection).find(new BsonDocument("login",
            new BsonDocument("$in", new org.bson.BsonArray(List.of(new BsonString("a"), new BsonString("b"))))));
    }

    @Test
    public void testNullableParameterBecomesBsonNull() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"login\\": :login}")
                List<TestEntity> findByLogin(@Nullable String login);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("findByLogin", new Object[]{null});

        verify(this.executor.collection).find(new BsonDocument("login", org.bson.BsonNull.VALUE));
    }

    @Test
    public void testUpdateReturnsUpdateCount() {
        Mockito.when(this.executor.updateResult.getModifiedCount()).thenReturn(3L);

        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoUpdate(filter = "{\\"_id\\": :id}", update = "{\\"$set\\": {\\"login\\": :login}}")
                UpdateCount rename(ObjectId id, String login);
            }
            """);

        var id = new ObjectId();
        var result = repository.invoke("rename", id, "user");

        assertThat(result).isEqualTo(new io.koraframework.database.common.UpdateCount(3));
        verify(this.executor.collection).updateOne(
            org.mockito.ArgumentMatchers.eq(new BsonDocument("_id", new BsonObjectId(id))),
            org.mockito.ArgumentMatchers.eq(new BsonDocument("$set", new BsonDocument("login", new BsonString("user")))),
            any());
    }

    @Test
    public void testEntityFieldPlaceholder() {
        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoUpdate(filter = "{\\"_id\\": :user.id}", update = "{\\"$set\\": {\\"login\\": :user.login}}")
                UpdateCount rename(TestEntity user);
            }
            """, """
            public record TestEntity(ObjectId id, String login) {}
            """);

        var id = new ObjectId();
        repository.invoke("rename", newObject("TestEntity", id, "user"));

        verify(this.executor.collection).updateOne(
            org.mockito.ArgumentMatchers.eq(new BsonDocument("_id", new BsonObjectId(id))),
            org.mockito.ArgumentMatchers.eq(new BsonDocument("$set", new BsonDocument("login", new BsonString("user")))),
            any());
    }

    @Test
    public void testNestedEntityFieldPlaceholder() {
        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{\\"city\\": :user.address.city}")
                long countInSameCity(TestEntity user);
            }
            """, """
            public record TestEntity(String login, TestAddress address) {}
            """, """
            public record TestAddress(String city) {}
            """);

        repository.invoke("countInSameCity", newObject("TestEntity", "user", newObject("TestAddress", "Moscow")));

        verify(this.executor.collection).countDocuments(new BsonDocument("city", new BsonString("Moscow")));
    }

    @Test
    public void testDeleteMany() {
        Mockito.when(this.executor.deleteResult.getDeletedCount()).thenReturn(2L);

        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoDelete(filter = "{\\"active\\": false}", many = true)
                long deleteInactive();
            }
            """);

        var result = repository.invoke("deleteInactive");

        assertThat(result).isEqualTo(2L);
        verify(this.executor.collection).deleteMany(new BsonDocument("active", org.bson.BsonBoolean.FALSE));
    }

    @Test
    public void testCount() {
        Mockito.when(this.executor.collection.countDocuments(any(org.bson.conversions.Bson.class))).thenReturn(7L);

        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{\\"active\\": true}")
                long countActive();
            }
            """);

        assertThat((Object) repository.invoke("countActive")).isEqualTo(7L);
    }

    @Test
    public void testInsertOne() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                void insert(TestEntity entity);
            }
            """, """
            @MongoCollection("users")
            public record TestEntity(String login) {}
            """);

        var entity = newObject("TestEntity", "user");
        repository.invoke("insert", entity);

        verify(this.executor.database).getCollection(org.mockito.ArgumentMatchers.eq("users"), any(Class.class));
        verify(this.executor.collection).insertOne(entity);
    }

    @Test
    public void testInsertMany() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                void insertAll(List<TestEntity> entities);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        var entities = List.of(newObject("TestEntity", "a"), newObject("TestEntity", "b"));
        repository.invoke("insertAll", entities);

        verify(this.executor.collection).insertMany(entities);
    }

    @Test
    public void testAggregate() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoAggregate("[{\\"$match\\": {\\"city\\": :city}}, {\\"$count\\": \\"total\\"}]")
                List<TestEntity> statsByCity(String city);
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("statsByCity", "Moscow");

        verify(this.executor.collection).aggregate(List.of(
            new BsonDocument("$match", new BsonDocument("city", new BsonString("Moscow"))),
            new BsonDocument("$count", new BsonString("total"))));
    }

    @Test
    public void testCollectionFromEntityAnnotation() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<TestEntity> findAll();
            }
            """, """
            @MongoCollection("people")
            public record TestEntity(String login) {}
            """);

        repository.invoke("findAll");

        verify(this.executor.database).getCollection(org.mockito.ArgumentMatchers.eq("people"), any(Class.class));
    }

    @Test
    public void testOperationCollectionAttributeWins() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", collection = "archive")
                List<TestEntity> findArchived();
            }
            """, """
            public record TestEntity(String login) {}
            """);

        repository.invoke("findArchived");

        verify(this.executor.database).getCollection(org.mockito.ArgumentMatchers.eq("archive"), any(Class.class));
    }
}
