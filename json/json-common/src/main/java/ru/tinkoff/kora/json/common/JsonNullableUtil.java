package ru.tinkoff.kora.json.common;

@SuppressWarnings("ALL")
final class JsonNullableUtil {

    static final JsonNullable NULL = new JsonNullable.Defined(null);
    static final JsonNullable UNDEFINED = new JsonNullable.Undefined();

    private JsonNullableUtil() {}
}
