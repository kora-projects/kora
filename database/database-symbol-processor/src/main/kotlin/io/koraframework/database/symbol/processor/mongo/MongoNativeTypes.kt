package io.koraframework.database.symbol.processor.mongo

import com.squareup.kotlinpoet.CodeBlock

/**
 * Types the generated code reads and writes directly, without going through a `Codec`.
 * Temporal types use the same BSON representation as the driver's own JSR-310 codecs, so documents stay readable
 * by a plain driver without Kora.
 *
 * @param write     produces a writer call for a value expression, the field name is written by the caller
 * @param read      produces an expression that reads the value from a reader
 * @param bsonValue produces an expression that wraps a value into a `BsonValue`, used for query parameters
 */
data class MongoNativeType(
    val write: (String, CodeBlock) -> CodeBlock,
    val read: (String) -> CodeBlock,
    val bsonValue: (CodeBlock) -> CodeBlock
)

object MongoNativeTypes {

    private val zoneOffset = com.squareup.kotlinpoet.ClassName("java.time", "ZoneOffset")
    private val instant = com.squareup.kotlinpoet.ClassName("java.time", "Instant")
    private val localDate = com.squareup.kotlinpoet.ClassName("java.time", "LocalDate")

    private val nativeTypes: Map<String, MongoNativeType> = mapOf(
        "kotlin.Boolean" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeBoolean(%L)", w, v) },
            { r -> CodeBlock.of("%N.readBoolean()", r) },
            { v -> CodeBlock.of("%T.valueOf(%L)", MongoTypes.bsonBoolean, v) }),
        "kotlin.Int" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeInt32(%L)", w, v) },
            { r -> CodeBlock.of("%N.readInt32()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonInt32, v) }),
        "kotlin.Short" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeInt32(%L.toInt())", w, v) },
            { r -> CodeBlock.of("%N.readInt32().toShort()", r) },
            { v -> CodeBlock.of("%T(%L.toInt())", MongoTypes.bsonInt32, v) }),
        "kotlin.Byte" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeInt32(%L.toInt())", w, v) },
            { r -> CodeBlock.of("%N.readInt32().toByte()", r) },
            { v -> CodeBlock.of("%T(%L.toInt())", MongoTypes.bsonInt32, v) }),
        "kotlin.Long" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeInt64(%L)", w, v) },
            { r -> CodeBlock.of("%N.readInt64()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonInt64, v) }),
        "kotlin.Double" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDouble(%L)", w, v) },
            { r -> CodeBlock.of("%N.readDouble()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonDouble, v) }),
        "kotlin.Float" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDouble(%L.toDouble())", w, v) },
            { r -> CodeBlock.of("%N.readDouble().toFloat()", r) },
            { v -> CodeBlock.of("%T(%L.toDouble())", MongoTypes.bsonDouble, v) }),
        "kotlin.String" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeString(%L)", w, v) },
            { r -> CodeBlock.of("%N.readString()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonString, v) }),
        "kotlin.ByteArray" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeBinaryData(%T(%L))", w, MongoTypes.bsonBinary, v) },
            { r -> CodeBlock.of("%N.readBinaryData().data", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonBinary, v) }),
        "org.bson.types.ObjectId" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeObjectId(%L)", w, v) },
            { r -> CodeBlock.of("%N.readObjectId()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonObjectId, v) }),
        "java.math.BigDecimal" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDecimal128(%T(%L))", w, MongoTypes.decimal128, v) },
            { r -> CodeBlock.of("%N.readDecimal128().bigDecimalValue()", r) },
            { v -> CodeBlock.of("%T(%T(%L))", MongoTypes.bsonDecimal128, MongoTypes.decimal128, v) }),
        "org.bson.types.Decimal128" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDecimal128(%L)", w, v) },
            { r -> CodeBlock.of("%N.readDecimal128()", r) },
            { v -> CodeBlock.of("%T(%L)", MongoTypes.bsonDecimal128, v) }),
        "java.time.Instant" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDateTime(%L.toEpochMilli())", w, v) },
            { r -> CodeBlock.of("%T.ofEpochMilli(%N.readDateTime())", instant, r) },
            { v -> CodeBlock.of("%T(%L.toEpochMilli())", MongoTypes.bsonDateTime, v) }),
        "java.time.LocalDate" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDateTime(%L.atStartOfDay(%T.UTC).toInstant().toEpochMilli())", w, v, zoneOffset) },
            { r -> CodeBlock.of("%T.ofEpochMilli(%N.readDateTime()).atZone(%T.UTC).toLocalDate()", instant, r, zoneOffset) },
            { v -> CodeBlock.of("%T(%L.atStartOfDay(%T.UTC).toInstant().toEpochMilli())", MongoTypes.bsonDateTime, v, zoneOffset) }),
        "java.time.LocalDateTime" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDateTime(%L.toInstant(%T.UTC).toEpochMilli())", w, v, zoneOffset) },
            { r -> CodeBlock.of("%T.ofEpochMilli(%N.readDateTime()).atZone(%T.UTC).toLocalDateTime()", instant, r, zoneOffset) },
            { v -> CodeBlock.of("%T(%L.toInstant(%T.UTC).toEpochMilli())", MongoTypes.bsonDateTime, v, zoneOffset) }),
        "java.time.LocalTime" to MongoNativeType(
            { w, v -> CodeBlock.of("%N.writeDateTime(%L.atDate(%T.EPOCH).toInstant(%T.UTC).toEpochMilli())", w, v, localDate, zoneOffset) },
            { r -> CodeBlock.of("%T.ofEpochMilli(%N.readDateTime()).atZone(%T.UTC).toLocalTime()", instant, r, zoneOffset) },
            { v -> CodeBlock.of("%T(%L.atDate(%T.EPOCH).toInstant(%T.UTC).toEpochMilli())", MongoTypes.bsonDateTime, v, localDate, zoneOffset) })
    )

    fun find(qualifiedName: String?): MongoNativeType? = qualifiedName?.let { nativeTypes[it] }
}
