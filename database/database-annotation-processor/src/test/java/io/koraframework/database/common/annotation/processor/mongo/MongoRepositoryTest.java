package io.koraframework.database.common.annotation.processor.mongo;

import com.mongodb.client.result.InsertManyResult;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    public void testTypesGeneratedInLaterRoundAreResolved() {
        compile(List.of(new io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor(),
            new LaterRoundProcessor(testPackage(), "LaterKind", "public enum LaterKind { A, B }")), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{\\"kind\\": :kind}")
                long countByKind(LaterKind kind);
            }
            """);
        compileResult.assertSuccess();

        var repository = new TestObject(compileResult.loadClass("$TestRepository_Impl"), List.<Object>of(this.executor));
        repository.invoke("countByKind", enumConstant("LaterKind", "B"));

        verify(this.executor.collection).countDocuments(new BsonDocument("kind", new BsonString("B")));
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
    public void testInsertReturnsGeneratedId() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                ObjectId insert(TestEntity entity);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        var result = repository.invoke("insert", newObject("TestEntity", null, "user"));

        assertThat(result).isEqualTo(this.executor.insertOneResult.getInsertedId().asObjectId().getValue());
    }

    @Test
    public void testInsertReturnsEntityCarryingTheId() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                TestEntity insert(TestEntity entity);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        var argument = newObject("TestEntity", null, "user");
        var result = repository.invoke("insert", argument);
        var id = this.executor.insertOneResult.getInsertedId().asObjectId().getValue();

        assertThat(result).isEqualTo(newObject("TestEntity", id, "user"));
        assertThat(result).isNotSameAs(argument);
    }

    @Test
    public void testInsertManyReturnsIdsInArgumentOrder() {
        var first = new ObjectId();
        var second = new ObjectId();
        this.executor.insertManyResult = InsertManyResult.acknowledged(
            Map.of(0, new BsonObjectId(first), 1, new BsonObjectId(second)));

        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                List<ObjectId> insertAll(List<TestEntity> entities);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        var result = repository.invoke("insertAll",
            List.of(newObject("TestEntity", null, "first"), newObject("TestEntity", null, "second")));

        assertThat((List<Object>) result).containsExactly(first, second);
    }

    @Test
    public void testInsertManyReturnsEntities() {
        var first = new ObjectId();
        var second = new ObjectId();
        this.executor.insertManyResult = InsertManyResult.acknowledged(
            Map.of(0, new BsonObjectId(first), 1, new BsonObjectId(second)));

        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                List<TestEntity> insertAll(List<TestEntity> entities);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        var result = repository.invoke("insertAll",
            List.of(newObject("TestEntity", null, "first"), newObject("TestEntity", null, "second")));

        assertThat((List<Object>) result).containsExactly(
            newObject("TestEntity", first, "first"), newObject("TestEntity", second, "second"));
    }

    @Test
    public void testInsertReturnsBeanEntityCarryingTheId() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                TestEntity insert(TestEntity entity);
            }
            """, """
            @EntityMongo
            public class TestEntity {
                @Id
                @Nullable
                private ObjectId id;
                private String login;

                public ObjectId getId() { return id; }
                public void setId(ObjectId id) { this.id = id; }

                public String getLogin() { return login; }
                public void setLogin(String login) { this.login = login; }
            }
            """);

        var argument = newJavaBean("TestEntity", null, "user");
        var result = repository.invoke("insert", argument);
        var id = this.executor.insertOneResult.getInsertedId().asObjectId().getValue();

        assertThat(result).isSameAs(argument);
        assertThat(invoke(result, "getId")).isEqualTo(id);
        assertThat(invoke(result, "getLogin")).isEqualTo("user");
    }

    @Test
    public void testInsertManyReturnsBeanEntities() {
        var first = new ObjectId();
        var second = new ObjectId();
        this.executor.insertManyResult = InsertManyResult.acknowledged(
            Map.of(0, new BsonObjectId(first), 1, new BsonObjectId(second)));

        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                List<TestEntity> insertAll(List<TestEntity> entities);
            }
            """, """
            @EntityMongo
            public class TestEntity {
                @Id
                @Nullable
                private ObjectId id;
                private String login;

                public ObjectId getId() { return id; }
                public void setId(ObjectId id) { this.id = id; }

                public String getLogin() { return login; }
                public void setLogin(String login) { this.login = login; }
            }
            """);

        var firstEntity = newJavaBean("TestEntity", null, "first");
        var secondEntity = newJavaBean("TestEntity", null, "second");

        var result = (List<Object>) repository.invoke("insertAll", List.of(firstEntity, secondEntity));

        assertThat(result.get(0)).isSameAs(firstEntity);
        assertThat(result.get(1)).isSameAs(secondEntity);
        assertThat(invoke(result.get(0), "getId")).isEqualTo(first);
        assertThat(invoke(result.get(1), "getId")).isEqualTo(second);
    }

    @Test
    public void testInsertOfAnEmptyCollectionNeverReachesTheDriver() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                List<ObjectId> insertAll(List<TestEntity> entities);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        var result = repository.invoke("insertAll", List.of());

        assertThat((List<?>) result).isEmpty();
        verify(this.executor.collection, Mockito.never()).insertMany(anyList());
    }

    @Test
    public void testInsertOfAnEmptyCollectionAsVoidNeverReachesTheDriver() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                void insertAll(List<TestEntity> entities);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        repository.invoke("insertAll", List.of());

        verify(this.executor.collection, Mockito.never()).insertMany(anyList());
    }

    @Test
    public void testInsertStillCompilesAsVoid() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                void insert(TestEntity entity);
            }
            """, """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);

        repository.invoke("insert", newObject("TestEntity", null, "user"));

        verify(this.executor.collection).insertOne(any());
    }

    @Test
    public void testBatchUpdateGoesThroughBulkWrite() {
        Mockito.when(this.executor.bulkWriteResult.getModifiedCount()).thenReturn(2);

        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoUpdate(filter = "{\\"_id\\": :users.id}", update = "{\\"$set\\": {\\"login\\": :users.login}}")
                UpdateCount renameAll(@Batch List<TestEntity> users);
            }
            """, """
            public record TestEntity(ObjectId id, String login) {}
            """);

        var result = repository.invoke("renameAll", List.of(
            newObject("TestEntity", new ObjectId(), "a"),
            newObject("TestEntity", new ObjectId(), "b")));

        assertThat(result).isEqualTo(new io.koraframework.database.common.UpdateCount(2));

        var models = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(this.executor.collection).bulkWrite(models.capture());
        assertThat(models.getValue()).hasSize(2);
        assertThat(models.getValue().getFirst()).isInstanceOf(com.mongodb.client.model.UpdateOneModel.class);
    }

    @Test
    public void testEmptyBatchDoesNotReachTheDriver() {
        var repository = compileMongo(List.of(), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoDelete(filter = "{\\"_id\\": :users.id}")
                UpdateCount deleteAll(@Batch List<TestEntity> users);
            }
            """, """
            public record TestEntity(ObjectId id, String login) {}
            """);

        var result = repository.invoke("deleteAll", List.of());

        assertThat(result).isEqualTo(new io.koraframework.database.common.UpdateCount(0));
        verify(this.executor.collection, org.mockito.Mockito.never()).bulkWrite(org.mockito.ArgumentMatchers.anyList());
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

    @Test
    public void testProjectionIsDerivedFromTheResultType() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument()
            .append("login", new BsonInt32(1))
            .append("age", new BsonInt32(1))
            .append("_id", new BsonInt32(0)));
    }

    @Test
    public void testDerivedProjectionKeepsIdWhenTheTypeHasOne() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(@Id ObjectId id, String login) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument()
            .append("_id", new BsonInt32(1))
            .append("login", new BsonInt32(1)));
    }

    @Test
    public void testExplicitProjectionWins() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 1}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, @Nullable Integer age) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument("login", new BsonInt32(1)));
    }

    @Test
    public void testNoProjectionForAnUnknownResultType() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<Document> raw();
            }
            """);

        repository.invoke("raw");

        verify(this.executor.findIterable, Mockito.never()).projection(any());
    }

    @Test
    public void testNoProjectionWhenAFieldNameHoldsADot() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(@Column("addr.city") String city) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable, Mockito.never()).projection(any());
    }

    @Test
    public void testProjectionMayOmitANullableField() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 1, \\"_id\\": 0}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, @Nullable Integer age) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument()
            .append("login", new BsonInt32(1))
            .append("_id", new BsonInt32(0)));
    }

    @Test
    public void testProjectionWithAPathCoversItsRoot() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"address.city\\": 1, \\"_id\\": 0}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(TestAddress address) {}
            """, """
            @EntityMongo
            public record TestAddress(@Nullable String city) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(any());
    }

    @Test
    public void testProjectionWithAnExpressionIsNotChecked() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"tags\\": {\\"$slice\\": 3}}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(List<String> tags, String login) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(any());
    }

    @Test
    public void testProjectionWithAFractionalInclusionValueIsAccepted() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 0.5, \\"age\\": 1}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument()
            .append("login", new BsonDouble(0.5))
            .append("age", new BsonInt32(1)));
    }

    @Test
    public void testProjectionWithAPlaceholderIsNotChecked() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": :flag}")
                List<TestSummary> summaries(int flag);
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """);

        repository.invoke("summaries", 1);

        verify(this.executor.findIterable).projection(any());
    }

    @Test
    public void testProjectionOverAPlainResultTypeIsNotChecked() {
        var repository = compileMongo(List.of(this.codec), """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 1}")
                List<TestEntity> summaries();
            }
            """, """
            public record TestEntity(String login, int age) {}
            """);

        repository.invoke("summaries");

        verify(this.executor.findIterable).projection(new BsonDocument("login", new BsonInt32(1)));
    }
}
