package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ClassName;

public class MongoTypes {

    public static final String MONGO_PACKAGE = "io.koraframework.database.mongo";
    public static final String ANNOTATION_PACKAGE = "io.koraframework.database.mongo.annotation";

    public static final ClassName REPOSITORY = ClassName.get(MONGO_PACKAGE, "MongoRepository");
    public static final ClassName EXECUTOR = ClassName.get(MONGO_PACKAGE, "MongoExecutor");
    public static final ClassName VALUES = ClassName.get(MONGO_PACKAGE, "MongoValues");

    public static final ClassName MONGO_ENTITY = ClassName.get(ANNOTATION_PACKAGE, "EntityMongo");
    public static final ClassName MONGO_COLLECTION = ClassName.get(ANNOTATION_PACKAGE, "MongoCollection");
    public static final ClassName FIND = ClassName.get(ANNOTATION_PACKAGE, "MongoFind");
    public static final ClassName INSERT = ClassName.get(ANNOTATION_PACKAGE, "MongoInsert");
    public static final ClassName UPDATE = ClassName.get(ANNOTATION_PACKAGE, "MongoUpdate");
    public static final ClassName REPLACE = ClassName.get(ANNOTATION_PACKAGE, "MongoReplace");
    public static final ClassName DELETE = ClassName.get(ANNOTATION_PACKAGE, "MongoDelete");
    public static final ClassName COUNT = ClassName.get(ANNOTATION_PACKAGE, "MongoCount");
    public static final ClassName AGGREGATE = ClassName.get(ANNOTATION_PACKAGE, "MongoAggregate");

    public static final ClassName CODEC = ClassName.get("org.bson.codecs", "Codec");
    public static final ClassName ENCODER_CONTEXT = ClassName.get("org.bson.codecs", "EncoderContext");
    public static final ClassName DECODER_CONTEXT = ClassName.get("org.bson.codecs", "DecoderContext");
    public static final ClassName BSON_WRITER = ClassName.get("org.bson", "BsonWriter");
    public static final ClassName BSON_READER = ClassName.get("org.bson", "BsonReader");
    public static final ClassName BSON_TYPE = ClassName.get("org.bson", "BsonType");
    public static final ClassName BSON_BINARY = ClassName.get("org.bson", "BsonBinary");
    public static final ClassName BSON_DOCUMENT = ClassName.get("org.bson", "BsonDocument");
    public static final ClassName BSON_ARRAY = ClassName.get("org.bson", "BsonArray");
    public static final ClassName BSON_VALUE = ClassName.get("org.bson", "BsonValue");
    public static final ClassName BSON_NULL = ClassName.get("org.bson", "BsonNull");
    public static final ClassName BSON_STRING = ClassName.get("org.bson", "BsonString");
    public static final ClassName BSON_BOOLEAN = ClassName.get("org.bson", "BsonBoolean");
    public static final ClassName BSON_INT32 = ClassName.get("org.bson", "BsonInt32");
    public static final ClassName BSON_INT64 = ClassName.get("org.bson", "BsonInt64");
    public static final ClassName BSON_DOUBLE = ClassName.get("org.bson", "BsonDouble");
    public static final ClassName BSON_DATE_TIME = ClassName.get("org.bson", "BsonDateTime");
    public static final ClassName BSON_DECIMAL128 = ClassName.get("org.bson", "BsonDecimal128");
    public static final ClassName BSON_OBJECT_ID = ClassName.get("org.bson", "BsonObjectId");
    public static final ClassName BSON_REGULAR_EXPRESSION = ClassName.get("org.bson", "BsonRegularExpression");
    public static final ClassName OBJECT_ID = ClassName.get("org.bson.types", "ObjectId");
    public static final ClassName DECIMAL_128 = ClassName.get("org.bson.types", "Decimal128");

    public static final ClassName CODEC_REGISTRY = ClassName.get("org.bson.codecs.configuration", "CodecRegistry");
    public static final ClassName CODEC_REGISTRIES = ClassName.get("org.bson.codecs.configuration", "CodecRegistries");
    public static final ClassName BSON = ClassName.get("org.bson.conversions", "Bson");

    public static final ClassName MONGO_CLIENT_SETTINGS = ClassName.get("com.mongodb", "MongoClientSettings");
    public static final ClassName CLIENT_SESSION = ClassName.get("com.mongodb.client", "ClientSession");
    public static final ClassName MONGO_DATABASE = ClassName.get("com.mongodb.client", "MongoDatabase");
    public static final ClassName UPDATE_OPTIONS = ClassName.get("com.mongodb.client.model", "UpdateOptions");
    public static final ClassName REPLACE_OPTIONS = ClassName.get("com.mongodb.client.model", "ReplaceOptions");
}
