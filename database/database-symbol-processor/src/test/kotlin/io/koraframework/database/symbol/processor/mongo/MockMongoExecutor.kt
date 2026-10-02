package io.koraframework.database.symbol.processor.mongo

import com.mongodb.TransactionOptions
import com.mongodb.client.AggregateIterable
import com.mongodb.client.ClientSession
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.UpdateResult
import io.koraframework.database.common.telemetry.DatabaseTelemetry
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetry
import io.koraframework.database.mongo.MongoExecutor
import org.bson.BsonObjectId
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.util.function.Supplier

@Suppress("UNCHECKED_CAST")
class MockMongoExecutor : MongoExecutor {

    val client: MongoClient = Mockito.mock(MongoClient::class.java)
    val database: MongoDatabase = Mockito.mock(MongoDatabase::class.java)
    val collection: MongoCollection<Any> = Mockito.mock(MongoCollection::class.java) as MongoCollection<Any>
    val findIterable: FindIterable<Any> = Mockito.mock(FindIterable::class.java) as FindIterable<Any>
    val aggregateIterable: AggregateIterable<Any> = Mockito.mock(AggregateIterable::class.java) as AggregateIterable<Any>
    val updateResult: UpdateResult = Mockito.mock(UpdateResult::class.java)
    val deleteResult: DeleteResult = Mockito.mock(DeleteResult::class.java)
    val bulkWriteResult: BulkWriteResult = Mockito.mock(BulkWriteResult::class.java)
    val insertOneResult: InsertOneResult = InsertOneResult.acknowledged(BsonObjectId(ObjectId()))
    var insertManyResult: InsertManyResult = InsertManyResult.acknowledged(mapOf())
    var telemetry: DatabaseTelemetry = NoopDatabaseTelemetry.INSTANCE

    init {
        reset()
    }

    fun reset() {
        Mockito.reset(client, database, collection, findIterable, aggregateIterable, updateResult, deleteResult, bulkWriteResult)
        telemetry = NoopDatabaseTelemetry.INSTANCE

        Mockito.doReturn(collection).`when`(database).getCollection(anyString())
        Mockito.doReturn(collection).`when`(database).getCollection(anyString(), any(Class::class.java))
        Mockito.doReturn(collection).`when`(collection).withCodecRegistry(any())

        Mockito.doReturn(findIterable).`when`(collection).find(any(Bson::class.java))
        Mockito.doReturn(findIterable).`when`(findIterable).projection(any())
        Mockito.doReturn(findIterable).`when`(findIterable).sort(any())
        Mockito.doReturn(findIterable).`when`(findIterable).limit(Mockito.anyInt())
        Mockito.doReturn(findIterable).`when`(findIterable).skip(Mockito.anyInt())
        Mockito.doAnswer { it.getArgument(0) }.`when`(findIterable).into<MutableCollection<Any>>(any())

        Mockito.doReturn(aggregateIterable).`when`(collection).aggregate(anyList())
        Mockito.doAnswer { it.getArgument(0) }.`when`(aggregateIterable).into<MutableCollection<Any>>(any())

        Mockito.doReturn(updateResult).`when`(collection).updateOne(any(Bson::class.java), any(Bson::class.java), any(UpdateOptions::class.java))
        Mockito.doReturn(updateResult).`when`(collection).updateMany(any(Bson::class.java), any(Bson::class.java), any(UpdateOptions::class.java))
        Mockito.doReturn(updateResult).`when`(collection).replaceOne(any(Bson::class.java), any(), any(ReplaceOptions::class.java))
        Mockito.doReturn(deleteResult).`when`(collection).deleteOne(any(Bson::class.java))
        Mockito.doReturn(deleteResult).`when`(collection).deleteMany(any(Bson::class.java))
        Mockito.doReturn(0L).`when`(collection).countDocuments(any(Bson::class.java))
        Mockito.doReturn(bulkWriteResult).`when`(collection).bulkWrite(anyList())
        Mockito.doReturn(emptyList<Any>()).`when`(bulkWriteResult).upserts

        Mockito.doReturn(insertOneResult).`when`(collection).insertOne(any())
        Mockito.doAnswer { insertManyResult }.`when`(collection).insertMany(anyList())
    }

    override fun client(): MongoClient = client

    override fun database(): MongoDatabase = database

    override fun database(name: String): MongoDatabase = database

    override fun telemetry(): DatabaseTelemetry = telemetry

    override fun currentSession(): ClientSession? = null

    override fun <T : Any> inTx(options: TransactionOptions, callback: Supplier<T>): T = callback.get()
}
