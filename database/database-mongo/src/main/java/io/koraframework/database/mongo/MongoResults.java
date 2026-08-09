package io.koraframework.database.mongo;

import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Русский</b>: Чтение идентификаторов из результатов записи драйвера, используется сгенерированным кодом репозиториев.
 * <hr>
 * <b>English</b>: Reads identifiers out of the driver's write results, used by generated repository code.
 */
public final class MongoResults {

    private static final String UNACKNOWLEDGED = """
        Insert returned no identifier because the write concern is unacknowledged.
        Declare the repository method as void, or use an acknowledged write concern.""";

    private MongoResults() {}

    public static ObjectId insertedId(InsertOneResult result) {
        if (!result.wasAcknowledged()) {
            throw new IllegalStateException(UNACKNOWLEDGED);
        }
        return objectId(result.getInsertedId(), -1);
    }

    public static List<ObjectId> insertedIds(InsertManyResult result, int expected) {
        if (!result.wasAcknowledged()) {
            throw new IllegalStateException(UNACKNOWLEDGED);
        }
        var inserted = result.getInsertedIds();
        var ids = new ArrayList<ObjectId>(expected);
        for (int i = 0; i < expected; i++) {
            ids.add(objectId(inserted.get(i), i));
        }
        return List.copyOf(ids);
    }

    private static ObjectId objectId(@Nullable BsonValue value, int index) {
        var at = index < 0 ? "" : " at index " + index;
        if (value == null) {
            throw new IllegalStateException("Insert reported no identifier for the document" + at);
        }
        if (!value.isObjectId()) {
            throw new IllegalStateException("Identifier of the inserted document" + at + " is not an ObjectId but " + value.getBsonType());
        }
        return value.asObjectId().getValue();
    }
}
