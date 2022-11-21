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
    NULLABLE_STRING_HEADER(MemberName(extractorsPackage, "extractNullableStringHeaderParameter")),
    LIST_STRING_HEADER(MemberName(extractorsPackage, "extractStringListHeaderParameter")),

    STRING_QUERY(MemberName(extractorsPackage, "extractStringQueryParameter")),
    NULLABLE_STRING_QUERY(MemberName(extractorsPackage, "extractNullableStringQueryParameter")),
    LIST_STRING_QUERY(MemberName(extractorsPackage, "extractStringListQueryParameter")),
    INT_QUERY(MemberName(extractorsPackage, "extractIntQueryParameter")),
    NULLABLE_INT_QUERY(MemberName(extractorsPackage, "extractNullableIntQueryParameter")),
    LIST_INT_QUERY(MemberName(extractorsPackage, "extractIntListQueryParameter")),
    LONG_QUERY(MemberName(extractorsPackage, "extractLongQueryParameter")),
    NULLABLE_LONG_QUERY(MemberName(extractorsPackage, "extractNullableLongQueryParameter")),
    LIST_LONG_QUERY(MemberName(extractorsPackage, "extractLongListQueryParameter")),
    DOUBLE_QUERY(MemberName(extractorsPackage, "extractDoubleQueryParameter")),
    NULLABLE_DOUBLE_QUERY(MemberName(extractorsPackage, "extractNullableDoubleQueryParameter")),
    LIST_DOUBLE_QUERY(MemberName(extractorsPackage, "extractDoubleListQueryParameter")),
    BOOLEAN_QUERY(MemberName(extractorsPackage, "extractBooleanQueryParameter")),
    NULLABLE_BOOLEAN_QUERY(MemberName(extractorsPackage, "extractNullableBooleanQueryParameter")),
    LIST_BOOLEAN_QUERY(MemberName(extractorsPackage, "extractBooleanListQueryParameter")),
}
