package ru.tinkoff.kora.http.server.symbol.procesor

import com.squareup.kotlinpoet.MemberName

private const val extractorsPackage = "ru.tinkoff.kora.http.server.common.handler"

enum class ExtractorFunction(val memberName: MemberName) {
    STRING_PATH(MemberName(extractorsPackage, "extractStringPathParameter")),
    UUID_PATH(MemberName(extractorsPackage, "extractUUIDPathParameter")),
    INT_PATH(MemberName(extractorsPackage, "extractIntPathParameter")),
    LONG_PATH(MemberName(extractorsPackage, "extractLongPathParameter")),
    DOUBLE_PATH(MemberName(extractorsPackage, "extractDoublePathParameter")),

    STRING_HEADER(MemberName(extractorsPackage, "extractStringHeaderParameter")),
    STRING_NULLABLE_HEADER(MemberName(extractorsPackage, "extractNullableStringHeaderParameter")),
    LIST_STRING_HEADER(MemberName(extractorsPackage, "extractStringListHeaderParameter")),
    LIST_STRING_NULLABLE_HEADER(MemberName(extractorsPackage, "extractNullableStringListHeaderParameter")),

    UUID_QUERY(MemberName(extractorsPackage, "extractUUIDQueryParameter")),
    UUID_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableUUIDQueryParameter")),
    UUID_LIST_QUERY(MemberName(extractorsPackage, "extractUUIDListQueryParameter")),
    UUID_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableUUIDListQueryParameter")),
    STRING_QUERY(MemberName(extractorsPackage, "extractStringQueryParameter")),
    STRING_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableStringQueryParameter")),
    STRING_LIST_QUERY(MemberName(extractorsPackage, "extractStringListQueryParameter")),
    STRING_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableStringListQueryParameter")),
    INT_QUERY(MemberName(extractorsPackage, "extractIntQueryParameter")),
    INT_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableIntQueryParameter")),
    INT_LIST_QUERY(MemberName(extractorsPackage, "extractIntListQueryParameter")),
    INT_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableIntListQueryParameter")),
    LONG_QUERY(MemberName(extractorsPackage, "extractLongQueryParameter")),
    LONG_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableLongQueryParameter")),
    LONG_LIST_QUERY(MemberName(extractorsPackage, "extractLongListQueryParameter")),
    LONG_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableLongListQueryParameter")),
    DOUBLE_QUERY(MemberName(extractorsPackage, "extractDoubleQueryParameter")),
    DOUBLE_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableDoubleQueryParameter")),
    DOUBLE_LIST_QUERY(MemberName(extractorsPackage, "extractDoubleListQueryParameter")),
    DOUBLE_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableDoubleListQueryParameter")),
    BOOLEAN_QUERY(MemberName(extractorsPackage, "extractBooleanQueryParameter")),
    BOOLEAN_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableBooleanQueryParameter")),
    BOOLEAN_LIST_QUERY(MemberName(extractorsPackage, "extractBooleanListQueryParameter")),
    BOOLEAN_LIST_NULLABLE_QUERY(MemberName(extractorsPackage, "extractNullableBooleanListQueryParameter")),
}
