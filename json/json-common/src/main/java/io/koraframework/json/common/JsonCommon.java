package io.koraframework.json.common;

public final class JsonCommon {

    private JsonCommon() {
    }

    public static final String ERR_PREFIX = "Failed to read json";
    public static final String ERR_REQUIRED = ": required field must not be null";
    public static final String ERR_MISSING = ": missing required field(s): ";
    public static final String ERR_EXPECTED = ": expected ";
    public static final String ERR_GOT = ", but got ";

    public static final String ERR_OBJECT_EXPECTED = "an object '{...}'";
    public static final String ERR_FIELD_NAME_EXPECTED = "a field name";
    public static final String ERR_INTEGER_EXPECTED = "an integer number";
    public static final String ERR_FLOAT_EXPECTED = "a fractional number";
    public static final String ERR_STRING_EXPECTED = "a string";
    public static final String ERR_BOOLEAN_EXPECTED = "a boolean"; // ДОБАВЛЕНО
    public static final String ERR_BINARY_EXPECTED = "a base64-encoded string";
    public static final String ERR_UUID_EXPECTED = "a UUID string";
    public static final String ERR_NUMBER_EXPECTED = "a number";
    public static final String ERR_ROOT = "<root>";

    public static final String T_NOTHING = "nothing (end of input)";
    public static final String T_NULL = "null";
    public static final String T_OBJECT = "an object";
    public static final String T_ARRAY = "an array";
    public static final String T_TRUNCATED = "...(truncated)";

    public static final String T_ACTUAL_STRING_PREFIX = "a string \"";
    public static final String T_ACTUAL_STRING_SUFFIX = "\"";
    public static final String T_ACTUAL_NUMBER = "a number ";
    public static final String T_ACTUAL_FLOAT = "a fractional number ";
    public static final String T_ACTUAL_BOOLEAN = "a boolean ";
    public static final String T_ACTUAL_TOKEN = "token ";
}
