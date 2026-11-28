package io.koraframework.json.common;

import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;

import static io.koraframework.json.common.JsonCommon.*;

public final class JsonCommonReader {

    private JsonCommonReader() {}

    public static <T> T readRequiredObject(JsonParser parser, JsonReader<T> reader, String className, String fieldName) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) {
            throw __requiredFieldNull(parser, className, fieldName);
        }
        return reader.read(parser);
    }

    public static String readString(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return parser.getString();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static Long readLongObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getLongValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static long readLongPrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getLongValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static Integer readIntegerObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getIntValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static int readIntegerPrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getIntValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static Boolean readBooleanObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_TRUE) return Boolean.TRUE;
        if (token == JsonToken.VALUE_FALSE) return Boolean.FALSE;
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_BOOLEAN_EXPECTED);
    }

    public static boolean readBooleanPrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_TRUE) return true;
        if (token == JsonToken.VALUE_FALSE) return false;
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_BOOLEAN_EXPECTED);
    }

    public static UUID readUUID(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return UUID.fromString(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_UUID_EXPECTED);
    }

    public static Byte readByteObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getByteValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static byte readBytePrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getByteValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static Short readShortObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getShortValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static short readShortPrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getShortValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static Double readDoubleObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) return parser.getDoubleValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_FLOAT_EXPECTED);
    }

    public static double readDoublePrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) return parser.getDoubleValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_FLOAT_EXPECTED);
    }

    public static Float readFloatObject(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) return parser.getFloatValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_FLOAT_EXPECTED);
    }

    public static float readFloatPrimitive(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) return parser.getFloatValue();
        if (token == JsonToken.VALUE_NULL) throw __requiredFieldNull(parser, className, member);
        throw __unexpectedToken(parser, className, member, ERR_FLOAT_EXPECTED);
    }

    public static BigInteger readBigInteger(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_INT) return parser.getBigIntegerValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_INTEGER_EXPECTED);
    }

    public static BigDecimal readBigDecimal(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) return parser.getDecimalValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_FLOAT_EXPECTED);
    }

    public static byte[] readBinary(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING || token == JsonToken.VALUE_EMBEDDED_OBJECT) return parser.getBinaryValue();
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static Instant readInstant(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return Instant.parse(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static LocalDate readLocalDate(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return LocalDate.parse(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static LocalDateTime readLocalDateTime(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return LocalDateTime.parse(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static OffsetDateTime readOffsetDateTime(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return OffsetDateTime.parse(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static ZonedDateTime readZonedDateTime(JsonParser parser, String className, String member) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_STRING) return ZonedDateTime.parse(parser.getString());
        if (token == JsonToken.VALUE_NULL) return null;
        throw __unexpectedToken(parser, className, member, ERR_STRING_EXPECTED);
    }

    public static String __jsonPath(JsonParser parser) {
        var p = parser.streamReadContext().pathAsPointer().toString();
        return p.isEmpty() ? ERR_ROOT : p;
    }

    public static String __actualValue(JsonParser parser) {
        var t = parser.currentToken();
        if (t == null) return T_NOTHING;
        var v = parser.getValueAsString();
        if (v != null && v.length() > 128) v = v.substring(0, 128) + T_TRUNCATED;
        if (t == JsonToken.VALUE_NULL) return T_NULL;
        if (t == JsonToken.START_OBJECT) return T_OBJECT;
        if (t == JsonToken.START_ARRAY) return T_ARRAY;
        if (t == JsonToken.VALUE_STRING) return T_ACTUAL_STRING_PREFIX + v + T_ACTUAL_STRING_SUFFIX;
        if (t == JsonToken.VALUE_NUMBER_INT) return T_ACTUAL_NUMBER + v;
        if (t == JsonToken.VALUE_NUMBER_FLOAT) return T_ACTUAL_FLOAT + v;
        if (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE) return T_ACTUAL_BOOLEAN + v;
        return T_ACTUAL_TOKEN + t;
    }

    public static StreamReadException __unexpectedToken(JsonParser parser, String className, String errorCode) {
        return __unexpectedToken(parser, className, "", errorCode);
    }

    public static StreamReadException __unexpectedToken(JsonParser parser, String className, String member, String expected) {
        return new StreamReadException(parser, ERR_PREFIX + className + "." + member + ERR_EXPECTED + expected + ERR_GOT + __actualValue(parser) + " (at " + __jsonPath(parser) + ")");
    }

    public static StreamReadException __requiredFieldNull(JsonParser parser, String className, String member) {
        return new StreamReadException(parser, ERR_PREFIX + className + "." + member + ERR_REQUIRED + " (at " + __jsonPath(parser) + ")");
    }

    public static StreamReadException createMissingRequiredFieldsException(JsonParser parser, String className, String missingFields) {
        String message = ERR_PREFIX + className + ERR_MISSING + missingFields + " (at " + __jsonPath(parser) + ")";
        return new StreamReadException(parser, message);
    }
}
