package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Types the generated code reads and writes directly, without going through a {@code Codec}.
 * Temporal types use the same BSON representation as the driver's own JSR-310 codecs, so documents stay readable
 * by a plain driver without Kora.
 */
public final class MongoNativeTypes {

    /**
     * @param write     produces a writer call for a value expression, the field name is written by the caller
     * @param read      produces an expression that reads the value from a reader
     * @param bsonValue produces an expression that wraps a value into a {@code BsonValue}, used for query parameters
     */
    public record MongoNativeType(TypeName typeName,
                                  BiFunction<String, CodeBlock, CodeBlock> write,
                                  Function<String, CodeBlock> read,
                                  Function<CodeBlock, CodeBlock> bsonValue) {}

    private static final ClassName BSON_BINARY = MongoTypes.BSON_BINARY;
    private static final ClassName DECIMAL_128 = MongoTypes.DECIMAL_128;

    private static final Map<TypeName, MongoNativeType> NATIVE_TYPES = Map.ofEntries(
        entry(TypeName.get(Boolean.class),
            (w, v) -> CodeBlock.of("$N.writeBoolean($L)", w, v),
            r -> CodeBlock.of("$N.readBoolean()", r),
            v -> CodeBlock.of("$T.valueOf($L)", MongoTypes.BSON_BOOLEAN, v)),
        entry(TypeName.get(Integer.class),
            (w, v) -> CodeBlock.of("$N.writeInt32($L)", w, v),
            r -> CodeBlock.of("$N.readInt32()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_INT32, v)),
        entry(TypeName.get(Short.class),
            (w, v) -> CodeBlock.of("$N.writeInt32($L)", w, v),
            r -> CodeBlock.of("(short) $N.readInt32()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_INT32, v)),
        entry(TypeName.get(Byte.class),
            (w, v) -> CodeBlock.of("$N.writeInt32($L)", w, v),
            r -> CodeBlock.of("(byte) $N.readInt32()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_INT32, v)),
        entry(TypeName.get(Long.class),
            (w, v) -> CodeBlock.of("$N.writeInt64($L)", w, v),
            r -> CodeBlock.of("$N.readInt64()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_INT64, v)),
        entry(TypeName.get(Double.class),
            (w, v) -> CodeBlock.of("$N.writeDouble($L)", w, v),
            r -> CodeBlock.of("$N.readDouble()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_DOUBLE, v)),
        entry(TypeName.get(Float.class),
            (w, v) -> CodeBlock.of("$N.writeDouble($L)", w, v),
            r -> CodeBlock.of("(float) $N.readDouble()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_DOUBLE, v)),
        entry(TypeName.get(String.class),
            (w, v) -> CodeBlock.of("$N.writeString($L)", w, v),
            r -> CodeBlock.of("$N.readString()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_STRING, v)),
        entry(MongoTypes.OBJECT_ID,
            (w, v) -> CodeBlock.of("$N.writeObjectId($L)", w, v),
            r -> CodeBlock.of("$N.readObjectId()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_OBJECT_ID, v)),
        entry(ArrayTypeName.of(TypeName.BYTE),
            (w, v) -> CodeBlock.of("$N.writeBinaryData(new $T($L))", w, BSON_BINARY, v),
            r -> CodeBlock.of("$N.readBinaryData().getData()", r),
            v -> CodeBlock.of("new $T($L)", BSON_BINARY, v)),
        entry(TypeName.get(BigDecimal.class),
            (w, v) -> CodeBlock.of("$N.writeDecimal128(new $T($L))", w, DECIMAL_128, v),
            r -> CodeBlock.of("$N.readDecimal128().bigDecimalValue()", r),
            v -> CodeBlock.of("new $T(new $T($L))", MongoTypes.BSON_DECIMAL128, DECIMAL_128, v)),
        entry(DECIMAL_128,
            (w, v) -> CodeBlock.of("$N.writeDecimal128($L)", w, v),
            r -> CodeBlock.of("$N.readDecimal128()", r),
            v -> CodeBlock.of("new $T($L)", MongoTypes.BSON_DECIMAL128, v)),
        entry(TypeName.get(Instant.class),
            (w, v) -> CodeBlock.of("$N.writeDateTime($L.toEpochMilli())", w, v),
            r -> CodeBlock.of("$T.ofEpochMilli($N.readDateTime())", Instant.class, r),
            v -> CodeBlock.of("new $T($L.toEpochMilli())", MongoTypes.BSON_DATE_TIME, v)),
        entry(TypeName.get(LocalDate.class),
            (w, v) -> CodeBlock.of("$N.writeDateTime($L.atStartOfDay($T.UTC).toInstant().toEpochMilli())", w, v, ZoneOffset.class),
            r -> CodeBlock.of("$T.ofEpochMilli($N.readDateTime()).atZone($T.UTC).toLocalDate()", Instant.class, r, ZoneOffset.class),
            v -> CodeBlock.of("new $T($L.atStartOfDay($T.UTC).toInstant().toEpochMilli())", MongoTypes.BSON_DATE_TIME, v, ZoneOffset.class)),
        entry(TypeName.get(LocalDateTime.class),
            (w, v) -> CodeBlock.of("$N.writeDateTime($L.toInstant($T.UTC).toEpochMilli())", w, v, ZoneOffset.class),
            r -> CodeBlock.of("$T.ofEpochMilli($N.readDateTime()).atZone($T.UTC).toLocalDateTime()", Instant.class, r, ZoneOffset.class),
            v -> CodeBlock.of("new $T($L.toInstant($T.UTC).toEpochMilli())", MongoTypes.BSON_DATE_TIME, v, ZoneOffset.class)),
        entry(TypeName.get(LocalTime.class),
            (w, v) -> CodeBlock.of("$N.writeDateTime($L.atDate($T.EPOCH).toInstant($T.UTC).toEpochMilli())", w, v, LocalDate.class, ZoneOffset.class),
            r -> CodeBlock.of("$T.ofEpochMilli($N.readDateTime()).atZone($T.UTC).toLocalTime()", Instant.class, r, ZoneOffset.class),
            v -> CodeBlock.of("new $T($L.atDate($T.EPOCH).toInstant($T.UTC).toEpochMilli())", MongoTypes.BSON_DATE_TIME, v, LocalDate.class, ZoneOffset.class))
    );

    private MongoNativeTypes() { }

    @Nullable
    public static MongoNativeType find(TypeName typeName) {
        return NATIVE_TYPES.get(typeName.box());
    }

    public static boolean isNative(TypeName typeName) {
        return find(typeName) != null;
    }

    private static Map.Entry<TypeName, MongoNativeType> entry(TypeName typeName,
                                                              BiFunction<String, CodeBlock, CodeBlock> write,
                                                              Function<String, CodeBlock> read,
                                                              Function<CodeBlock, CodeBlock> bsonValue) {
        return Map.entry(typeName, new MongoNativeType(typeName, write, read, bsonValue));
    }
}
