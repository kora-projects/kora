package io.koraframework.database.symbol.processor.mongo

import com.squareup.kotlinpoet.ClassName

object MongoTypes {

    private const val MONGO_PACKAGE = "io.koraframework.database.mongo"
    private const val ANNOTATION_PACKAGE = "io.koraframework.database.mongo.annotation"

    val repository = ClassName(MONGO_PACKAGE, "MongoRepository")
    val executor = ClassName(MONGO_PACKAGE, "MongoExecutor")
    val values = ClassName(MONGO_PACKAGE, "MongoValues")

    val mongoEntity = ClassName(ANNOTATION_PACKAGE, "EntityMongo")
    val mongoCollection = ClassName(ANNOTATION_PACKAGE, "MongoCollection")
    val find = ClassName(ANNOTATION_PACKAGE, "MongoFind")
    val insert = ClassName(ANNOTATION_PACKAGE, "MongoInsert")
    val update = ClassName(ANNOTATION_PACKAGE, "MongoUpdate")
    val replace = ClassName(ANNOTATION_PACKAGE, "MongoReplace")
    val delete = ClassName(ANNOTATION_PACKAGE, "MongoDelete")
    val count = ClassName(ANNOTATION_PACKAGE, "MongoCount")
    val aggregate = ClassName(ANNOTATION_PACKAGE, "MongoAggregate")

    val codec = ClassName("org.bson.codecs", "Codec")
    val encoderContext = ClassName("org.bson.codecs", "EncoderContext")
    val decoderContext = ClassName("org.bson.codecs", "DecoderContext")
    val bsonWriter = ClassName("org.bson", "BsonWriter")
    val bsonReader = ClassName("org.bson", "BsonReader")
    val bsonType = ClassName("org.bson", "BsonType")
    val bsonBinary = ClassName("org.bson", "BsonBinary")
    val bsonDocument = ClassName("org.bson", "BsonDocument")
    val bsonArray = ClassName("org.bson", "BsonArray")
    val bsonValue = ClassName("org.bson", "BsonValue")
    val bsonNull = ClassName("org.bson", "BsonNull")
    val bsonString = ClassName("org.bson", "BsonString")
    val bsonBoolean = ClassName("org.bson", "BsonBoolean")
    val bsonInt32 = ClassName("org.bson", "BsonInt32")
    val bsonInt64 = ClassName("org.bson", "BsonInt64")
    val bsonDouble = ClassName("org.bson", "BsonDouble")
    val bsonDateTime = ClassName("org.bson", "BsonDateTime")
    val bsonDecimal128 = ClassName("org.bson", "BsonDecimal128")
    val bsonObjectId = ClassName("org.bson", "BsonObjectId")
    val bsonRegularExpression = ClassName("org.bson", "BsonRegularExpression")
    val objectId = ClassName("org.bson.types", "ObjectId")
    val decimal128 = ClassName("org.bson.types", "Decimal128")

    val codecRegistry = ClassName("org.bson.codecs.configuration", "CodecRegistry")
    val codecRegistries = ClassName("org.bson.codecs.configuration", "CodecRegistries")
    val bson = ClassName("org.bson.conversions", "Bson")

    val mongoClientSettings = ClassName("com.mongodb", "MongoClientSettings")
    val updateOptions = ClassName("com.mongodb.client.model", "UpdateOptions")
    val replaceOptions = ClassName("com.mongodb.client.model", "ReplaceOptions")
    val writeModel = ClassName("com.mongodb.client.model", "WriteModel")
    val updateOneModel = ClassName("com.mongodb.client.model", "UpdateOneModel")
    val updateManyModel = ClassName("com.mongodb.client.model", "UpdateManyModel")
    val replaceOneModel = ClassName("com.mongodb.client.model", "ReplaceOneModel")
    val deleteOneModel = ClassName("com.mongodb.client.model", "DeleteOneModel")
    val deleteManyModel = ClassName("com.mongodb.client.model", "DeleteManyModel")
    val document = ClassName("org.bson", "Document")
}
