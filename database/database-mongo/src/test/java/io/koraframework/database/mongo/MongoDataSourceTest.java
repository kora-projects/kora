package io.koraframework.database.mongo;

import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.test.mongo.MongoParams;
import io.koraframework.test.mongo.MongoTestContainer;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ExtendWith(MongoTestContainer.class)
class MongoDataSourceTest {

    @Test
    public void testStartsAndPings(MongoParams params) {
        MongoTestUtils.withDb(params, db -> {
            var result = db.database().runCommand(new BsonDocument("ping", new BsonInt32(1)));
            assertThat(result.getDouble("ok")).isEqualTo(1.0d);
            assertThat(probe(db)).isNull();
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
    public void testProbeReturnsFailureWhenServerIsDown() throws Exception {
        var db = MongoTestUtils.createDataSource(MongoTestUtils.config("mongodb://127.0.0.1:1", "test", Duration.ofMillis(200), false, true, Duration.ofSeconds(5)));

        MongoTestUtils.withDb(db, started -> {
            var failure = probe(started);
            assertThat(failure).isNotNull();
            assertThat(failure.message()).contains("ping failed");
        });
    }

    @Test
    public void testProbeTimesOutWithinReadinessTimeout() {
        var db = MongoTestUtils.createDataSource(MongoTestUtils.config("mongodb://127.0.0.1:1", "test", Duration.ofSeconds(30), false, true, Duration.ofMillis(300)));

        MongoTestUtils.withDb(db, started -> {
            var start = System.nanoTime();
            var failure = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> started.probe());
            assertThat(failure).isNotNull();
            assertThat(failure.message()).contains("timed out");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        });
    }

    @Test
    public void testProbeDisabledReturnsNullWhenServerIsDown() {
        var db = MongoTestUtils.createDataSource(MongoTestUtils.config("mongodb://127.0.0.1:1", "test", Duration.ofMillis(200), false, false, Duration.ofSeconds(5)));

        MongoTestUtils.withDb(db, started -> assertThat(probe(started)).isNull());
    }

    @Test
    public void testReadinessTimeoutMustBePositive() {
        var config = MongoTestUtils.config("mongodb://127.0.0.1:1", "test", Duration.ofMillis(200), false, true, Duration.ZERO);

        assertThatThrownBy(() -> MongoTestUtils.createDataSource(config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("readinessTimeout");
    }

    private static ReadinessProbeFailure probe(MongoDataSource db) {
        try {
            return db.probe();
        } catch (Exception e) {
            throw new AssertionError("probe must return a failure instead of throwing", e);
        }
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
