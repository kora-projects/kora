package io.koraframework.database.mongo;

import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoResultsTest {

    @Test
    void insertedIdReturnsTheGeneratedIdentifier() {
        var id = new ObjectId();

        assertThat(MongoResults.insertedId(InsertOneResult.acknowledged(new BsonObjectId(id)))).isEqualTo(id);
    }

    @Test
    void insertedIdRejectsAnUnacknowledgedWrite() {
        assertThatThrownBy(() -> MongoResults.insertedId(InsertOneResult.unacknowledged()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("write concern is unacknowledged");
    }

    @Test
    void insertedIdRejectsANonObjectIdIdentifier() {
        assertThatThrownBy(() -> MongoResults.insertedId(InsertOneResult.acknowledged(new BsonString("key"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not an ObjectId");
    }

    @Test
    void insertedIdsKeepsTheOrderOfTheInsertedDocuments() {
        var first = new ObjectId();
        var second = new ObjectId();
        var result = InsertManyResult.acknowledged(Map.of(0, new BsonObjectId(first), 1, new BsonObjectId(second)));

        assertThat(MongoResults.insertedIds(result, 2)).containsExactly(first, second);
    }

    @Test
    void insertedIdsRejectsAMissingIndex() {
        var result = InsertManyResult.acknowledged(Map.of(0, new BsonObjectId(new ObjectId())));

        assertThatThrownBy(() -> MongoResults.insertedIds(result, 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("index 1");
    }

    @Test
    void insertedIdsRejectsAnUnacknowledgedWrite() {
        assertThatThrownBy(() -> MongoResults.insertedIds(InsertManyResult.unacknowledged(), 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("write concern is unacknowledged");
    }
}
