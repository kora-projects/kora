package io.koraframework.database.mongo;

import io.koraframework.test.mongo.MongoParams;
import io.koraframework.test.mongo.MongoTestContainer;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MongoTestContainer.class)
class MongoDataSourceTest {

    @Test
    public void testStartsAndPings(MongoParams params) {
        MongoTestUtils.withDb(params, db -> {
            var result = db.database().runCommand(new BsonDocument("ping", new BsonInt32(1)));
            assertThat(result.getDouble("ok")).isEqualTo(1.0d);
            assertThat(db.probe()).isNull();
        });
    }

    @Test
    public void testFailsToStartOnUnreachableCluster() {
        var params = new MongoParams("mongodb://127.0.0.1:1", "test");
        var db = MongoTestUtils.createDataSource(params);

        assertThatThrownBy(db::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("failed to start");
    }

    @Test
    public void testClientIsUnavailableBeforeInit(MongoParams params) {
        var db = MongoTestUtils.createDataSource(params);

        assertThatThrownBy(db::client)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not initialized");
    }

    @Test
    public void testTransactionCommits(MongoParams params) {
        MongoTestUtils.withDb(params, db -> {
            var collection = db.database().getCollection("users");

            db.inTxWithoutResult(() -> {
                var session = db.currentSession();
                assertThat(session).isNotNull();
                collection.insertOne(session, new Document("login", "user"));
            });

            assertThat(collection.countDocuments()).isEqualTo(1);
        });
    }

    @Test
    public void testTransactionRollsBackOnError(MongoParams params) {
        MongoTestUtils.withDb(params, db -> {
            var collection = db.database().getCollection("users");

            assertThatThrownBy(() -> db.inTxWithoutResult(() -> {
                collection.insertOne(db.currentSession(), new Document("login", "user"));
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

            assertThat(collection.countDocuments()).isZero();
        });
    }

    @Test
    public void testNestedTransactionJoinsTheOuterOne(MongoParams params) {
        MongoTestUtils.withDb(params, db -> {
            var collection = db.database().getCollection("users");

            db.inTxWithoutResult(() -> {
                var outer = db.currentSession();
                db.inTxWithoutResult(() -> {
                    assertThat(db.currentSession()).isSameAs(outer);
                    collection.insertOne(db.currentSession(), new Document("login", "nested"));
                });
            });

            assertThat(collection.find(new BsonDocument("login", new BsonString("nested"))).first()).isNotNull();
        });
    }

    @Test
    public void testSessionIsAbsentOutsideTransaction(MongoParams params) {
        MongoTestUtils.withDb(params, db -> assertThat(db.currentSession()).isNull());
    }
}
