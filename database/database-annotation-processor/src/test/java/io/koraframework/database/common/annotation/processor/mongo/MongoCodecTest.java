package io.koraframework.database.common.annotation.processor.mongo;

import io.koraframework.database.annotation.processor.mongo.MongoEntityAnnotationProcessor;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MongoCodecTest extends AbstractMongoTest {

    @Test
    public void testRecordRoundTrip() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Id ObjectId id, String login, int age, @Nullable String comment) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var id = new ObjectId();
        var entity = newObject("TestEntity", id, "user", 42, null);

        var document = encode(codec, entity);
        assertThat(document.getObjectId("_id").getValue()).isEqualTo(id);
        assertThat(document.getString("login").getValue()).isEqualTo("user");
        assertThat(document.getInt32("age").getValue()).isEqualTo(42);
        assertThat(document.isNull("comment")).isTrue();

        assertThat(decode(codec, document)).isEqualTo(entity);
    }

    @Test
    public void testFieldTypeGeneratedInLaterRoundIsResolved() {
        compile(List.of(new MongoEntityAnnotationProcessor(), new LaterRoundProcessor(testPackage(), "LaterKind", "public enum LaterKind { A, B }")), """
            @EntityMongo
            public record TestEntity(String id, LaterKind kind, List<LaterKind> kinds) {}
            """);
        compileResult.assertSuccess();

        var kind = enumConstant("LaterKind", "B");
        var codec = codec("$TestEntity_MongoCodec");
        var entity = newObject("TestEntity", "1", kind, List.of(kind));

        assertThat(roundTrip(codec, entity)).isEqualTo(entity);
    }

    @Test
    public void testColumnRenamesDocumentField() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Column("user_login") String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var document = encode(codec, newObject("TestEntity", "user"));

        assertThat(document.containsKey("user_login")).isTrue();
        assertThat(document.containsKey("login")).isFalse();
    }

    @Test
    public void testFieldNameIsKeptAsIsWithoutColumn() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(String userLogin) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var document = encode(codec, newObject("TestEntity", "user"));

        assertThat(document.containsKey("userLogin")).isTrue();
    }

    @Test
    public void testUnknownDocumentFieldIsSkipped() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var document = new BsonDocument()
            .append("unknown", new BsonInt32(1))
            .append("login", new BsonString("user"))
            .append("alsoUnknown", new BsonDocument("nested", new BsonString("x")));

        assertThat(decode(codec, document)).isEqualTo(newObject("TestEntity", "user"));
    }

    @Test
    public void testAbsentNonNullableFieldFails() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");

        assertThatThrownBy(() -> decode(codec, new BsonDocument()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("login");
    }

    @Test
    public void testNullNonNullableFieldFailsOnEncode() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");

        assertThatThrownBy(() -> encode(codec, newObject("TestEntity", new Object[]{null})))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("login");
    }

    @Test
    public void testScalarTypesRoundTrip() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(boolean flag, long counter, double ratio, java.math.BigDecimal amount,
                                     java.util.UUID uuid, java.time.Instant at, java.time.LocalDate day) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec", new UuidCodec(UuidRepresentation.STANDARD));
        var entity = newObject("TestEntity", true, 10L, 1.5d, new BigDecimal("12.34"),
            UUID.randomUUID(), Instant.ofEpochMilli(1_700_000_000_000L), LocalDate.of(2026, 8, 9));

        assertThat(roundTrip(codec, entity)).isEqualTo(entity);
    }

    @Test
    public void testEnumRoundTrip() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(TestStatus status) {}
            """, """
            public enum TestStatus { ACTIVE, BLOCKED }
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var entity = newObject("TestEntity", enumConstant("TestStatus", "BLOCKED"));

        var document = encode(codec, entity);
        assertThat(document.getString("status").getValue()).isEqualTo("BLOCKED");
        assertThat(decode(codec, document)).isEqualTo(entity);
    }

    @Test
    public void testCollectionsRoundTrip() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(List<String> tags, Set<Integer> codes, Map<String, Long> counters) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var entity = newObject("TestEntity",
            List.of("a", "b"),
            java.util.Set.of(1, 2),
            Map.of("x", 10L));

        var document = encode(codec, entity);
        assertThat(document.getArray("tags").size()).isEqualTo(2);
        assertThat(document.getDocument("counters").getInt64("x").getValue()).isEqualTo(10L);
        assertThat(roundTrip(codec, entity)).isEqualTo(entity);
    }

    @Test
    public void testNullElementOfNonNullableCollectionFails() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(List<String> tags, Map<String, Long> counters) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var tags = new java.util.ArrayList<String>();
        tags.add(null);
        var counters = new java.util.HashMap<String, Long>();
        counters.put("x", null);

        assertThatThrownBy(() -> encode(codec, newObject("TestEntity", tags, Map.of())))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tags");
        assertThatThrownBy(() -> encode(codec, newObject("TestEntity", List.of(), counters)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("counters");

        var nullTag = new BsonDocument("tags", new org.bson.BsonArray(List.of(org.bson.BsonNull.VALUE))).append("counters", new BsonDocument());
        assertThatThrownBy(() -> decode(codec, nullTag))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tags");
        var nullCounter = new BsonDocument("tags", new org.bson.BsonArray()).append("counters", new BsonDocument("x", org.bson.BsonNull.VALUE));
        assertThatThrownBy(() -> decode(codec, nullCounter))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("counters");
    }

    @Test
    public void testNullElementOfNullableElementCollectionIsKept() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(List<@Nullable String> tags, Map<String, @Nullable Long> counters) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var tags = new java.util.ArrayList<String>();
        tags.add(null);
        var counters = new java.util.HashMap<String, Long>();
        counters.put("x", null);
        var entity = newObject("TestEntity", tags, counters);

        assertThat(roundTrip(codec, entity)).isEqualTo(entity);
    }

    @Test
    public void testNestedEntityUsesItsOwnCodec() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(String login, TestAddress address) {}
            """, """
            @EntityMongo
            public record TestAddress(String city) {}
            """);
        compileResult.assertSuccess();

        var addressCodec = codec("$TestAddress_MongoCodec");
        var codec = codec("$TestEntity_MongoCodec", addressCodec);
        var entity = newObject("TestEntity", "user", newObject("TestAddress", "Moscow"));

        var document = encode(codec, entity);
        assertThat(document.getDocument("address").getString("city").getValue()).isEqualTo("Moscow");
        assertThat(decode(codec, document)).isEqualTo(entity);
    }

    @Test
    public void testJavaBeanRoundTrip() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public class TestEntity {
                private String login;

                public String getLogin() { return login; }

                public void setLogin(String login) { this.login = login; }
            }
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var entity = newJavaBean("TestEntity", "user");

        var document = encode(codec, entity);
        assertThat(document.getString("login").getValue()).isEqualTo("user");
        assertThat(invoke(decode(codec, document), "getLogin")).isEqualTo("user");
    }

    @Test
    public void testEmbeddedIsRejected() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@io.koraframework.database.common.annotation.Embedded TestAddress address) {}
            """, """
            public record TestAddress(String city) {}
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.errors().getFirst().getMessage(null)).contains("@Embedded is not supported");
    }

    @Test
    public void testDuplicateDocumentFieldIsRejected() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Column("name") String login, String name) {}
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.errors().getFirst().getMessage(null)).contains("duplicate document field");
    }

    @Test
    public void testNullIdIsNotWrittenSoTheServerGeneratesIt() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var document = encode(codec, newObject("TestEntity", null, "user"));

        assertThat(document.containsKey("_id")).isFalse();
        assertThat(document.getString("login").getValue()).isEqualTo("user");
    }

    @Test
    public void testPresentIdIsStillWritten() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var id = new ObjectId();
        var document = encode(codec, newObject("TestEntity", id, "user"));

        assertThat(document.getObjectId("_id").getValue()).isEqualTo(id);
    }

    @Test
    public void testAbsentIdDecodesToNull() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Id @Nullable ObjectId id, String login) {}
            """);
        compileResult.assertSuccess();

        var codec = codec("$TestEntity_MongoCodec");
        var document = new BsonDocument().append("login", new BsonString("user"));

        assertThat(decode(codec, document)).isEqualTo(newObject("TestEntity", null, "user"));
    }

    @Test
    public void testNullableIdOfAnotherTypeIsRejected() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Id @Nullable String id, String login) {}
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.errors().getFirst().getMessage(null)).contains("Field mapped to '_id' is nullable but is not an ObjectId");
    }

    @Test
    public void testColumnMappedIdFollowsTheSameRule() {
        compile(List.of(new MongoEntityAnnotationProcessor()), """
            @EntityMongo
            public record TestEntity(@Column("_id") @Nullable String key, String login) {}
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.errors().getFirst().getMessage(null)).contains("Field mapped to '_id' is nullable but is not an ObjectId");
    }
}
