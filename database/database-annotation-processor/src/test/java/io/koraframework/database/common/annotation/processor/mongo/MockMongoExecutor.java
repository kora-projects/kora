package io.koraframework.database.common.annotation.processor.mongo;

import com.mongodb.TransactionOptions;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetry;
import io.koraframework.database.mongo.MongoExecutor;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MockMongoExecutor implements MongoExecutor {

    public final MongoClient client = Mockito.mock(MongoClient.class);
    public final MongoDatabase database = Mockito.mock(MongoDatabase.class);
    public final MongoCollection collection = Mockito.mock(MongoCollection.class);
    public final FindIterable findIterable = Mockito.mock(FindIterable.class);
    public final AggregateIterable aggregateIterable = Mockito.mock(AggregateIterable.class);
    public final UpdateResult updateResult = Mockito.mock(UpdateResult.class);
    public final DeleteResult deleteResult = Mockito.mock(DeleteResult.class);
    public final BulkWriteResult bulkWriteResult = Mockito.mock(BulkWriteResult.class);

    public MockMongoExecutor() {
        this.reset();
    }

    public void reset() {
        Mockito.reset(this.client, this.database, this.collection, this.findIterable, this.aggregateIterable, this.updateResult, this.deleteResult, this.bulkWriteResult);

        when(this.database.getCollection(anyString())).thenReturn(this.collection);
        when(this.database.getCollection(anyString(), any(Class.class))).thenReturn(this.collection);
        when(this.collection.withCodecRegistry(any())).thenReturn(this.collection);

        when(this.collection.find(any(Bson.class))).thenReturn(this.findIterable);
        when(this.findIterable.projection(any())).thenReturn(this.findIterable);
        when(this.findIterable.sort(any())).thenReturn(this.findIterable);
        when(this.findIterable.limit(Mockito.anyInt())).thenReturn(this.findIterable);
        when(this.findIterable.skip(Mockito.anyInt())).thenReturn(this.findIterable);
        when(this.findIterable.into(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(this.collection.aggregate(anyList())).thenReturn(this.aggregateIterable);
        when(this.aggregateIterable.into(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(this.collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class))).thenReturn(this.updateResult);
        when(this.collection.updateMany(any(Bson.class), any(Bson.class), any(UpdateOptions.class))).thenReturn(this.updateResult);
        when(this.collection.replaceOne(any(Bson.class), any(), any(ReplaceOptions.class))).thenReturn(this.updateResult);
        when(this.collection.deleteOne(any(Bson.class))).thenReturn(this.deleteResult);
        when(this.collection.deleteMany(any(Bson.class))).thenReturn(this.deleteResult);
        when(this.collection.countDocuments(any(Bson.class))).thenReturn(0L);
        when(this.collection.bulkWrite(anyList())).thenReturn(this.bulkWriteResult);
        when(this.bulkWriteResult.getUpserts()).thenReturn(List.of());
    }

    public <T> void mockFirst(@Nullable T value) {
        when(this.findIterable.first()).thenReturn(value);
    }

    public <T> void mockList(List<T> values) {
        when(this.findIterable.into(any())).thenAnswer(invocation -> {
            var target = (java.util.Collection<T>) invocation.getArgument(0);
            target.addAll(values);
            return target;
        });
    }

    @Override
    public MongoClient client() {
        return this.client;
    }

    @Override
    public MongoDatabase database() {
        return this.database;
    }

    @Override
    public MongoDatabase database(String name) {
        return this.database;
    }

    @Override
    public DatabaseTelemetry telemetry() {
        return NoopDatabaseTelemetry.INSTANCE;
    }

    @Nullable
    @Override
    public ClientSession currentSession() {
        return null;
    }

    @Override
    public <T> T inTx(TransactionOptions options, Supplier<T> callback) {
        return callback.get();
    }
}
