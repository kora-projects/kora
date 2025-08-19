package ru.tinkoff.kora.http.server.symbol.procesor

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy


object ExtractorFunctions {
    private val extractorsPackage = ClassName("ru.tinkoff.kora.http.server.common.handler", "RequestHandlerUtils")
    private val UUID = ClassName("java.util", "UUID")

    val path = mapOf<TypeName, MemberName>(
        BOOLEAN to MemberName(extractorsPackage, "parseBooleanPathParameter"),
        STRING to MemberName(extractorsPackage, "parseStringPathParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerPathParameter"),
        LONG to MemberName(extractorsPackage, "parseLongPathParameter"),
        DOUBLE to MemberName(extractorsPackage, "parseDoublePathParameter"),
        UUID to MemberName(extractorsPackage, "parseUUIDPathParameter"),
    )

    val header = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseStringHeaderParameter"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseOptionalStringHeaderParameter"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringListHeaderParameter"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringListHeaderParameter"),
        SET.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringSetHeaderParameter"),
        SET.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringSetHeaderParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerHeaderParameter"),
        INT.copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerHeaderParameter"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerListHeaderParameter"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerListHeaderParameter"),
        SET.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerSetHeaderParameter"),
        SET.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerSetHeaderParameter"),
        LONG to MemberName(extractorsPackage, "parseLongHeaderParameter"),
        LONG.copy(true) to MemberName(extractorsPackage, "parseOptionalLongHeaderParameter"),
        LIST.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseLongListHeaderParameter"),
        LIST.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseOptionalLongListHeaderParameter"),
        SET.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseLongSetHeaderParameter"),
        SET.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseOptionalLongSetHeaderParameter"),
        DOUBLE to MemberName(extractorsPackage, "parseDoubleHeaderParameter"),
        DOUBLE.copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleHeaderParameter"),
        LIST.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseDoubleListHeaderParameter"),
        LIST.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleListHeaderParameter"),
        SET.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseDoubleSetHeaderParameter"),
        SET.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleSetHeaderParameter"),
        UUID to MemberName(extractorsPackage, "parseUuidHeaderParameter"),
        UUID.copy(true) to MemberName(extractorsPackage, "parseOptionalUuidHeaderParameter"),
        LIST.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseUuidListHeaderParameter"),
        LIST.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseOptionalUuidListHeaderParameter"),
        SET.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseUuidSetHeaderParameter"),
        SET.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseOptionalUuidSetHeaderParameter"),
    )

    val cookie = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseCookieString"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseOptionalCookieString"),
        ClassName("ru.tinkoff.kora.http.common.cookie", "Cookie") to MemberName(extractorsPackage, "parseCookie"),
        ClassName("ru.tinkoff.kora.http.common.cookie", "Cookie").copy(true) to MemberName(extractorsPackage, "parseOptionalCookie"),
    )

    val query = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseStringQueryParameter"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseOptionalStringQueryParameter"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringListQueryParameter"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringListQueryParameter"),
        SET.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringSetQueryParameter"),
        SET.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringSetQueryParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerQueryParameter"),
        INT.copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerQueryParameter"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerListQueryParameter"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerListQueryParameter"),
        SET.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerSetQueryParameter"),
        SET.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerSetQueryParameter"),
        LONG to MemberName(extractorsPackage, "parseLongQueryParameter"),
        LONG.copy(true) to MemberName(extractorsPackage, "parseOptionalLongQueryParameter"),
        LIST.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseLongListQueryParameter"),
        LIST.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseOptionalLongListQueryParameter"),
        SET.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseLongSetQueryParameter"),
        SET.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseOptionalLongSetQueryParameter"),
        DOUBLE to MemberName(extractorsPackage, "parseDoubleQueryParameter"),
        DOUBLE.copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleQueryParameter"),
        LIST.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseDoubleListQueryParameter"),
        LIST.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleListQueryParameter"),
        SET.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseDoubleSetQueryParameter"),
        SET.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleSetQueryParameter"),
        BOOLEAN to MemberName(extractorsPackage, "parseBooleanQueryParameter"),
        BOOLEAN.copy(true) to MemberName(extractorsPackage, "parseOptionalBooleanQueryParameter"),
        LIST.parameterizedBy(BOOLEAN) to MemberName(extractorsPackage, "parseBooleanListQueryParameter"),
        LIST.parameterizedBy(BOOLEAN).copy(true) to MemberName(extractorsPackage, "parseOptionalBooleanListQueryParameter"),
        SET.parameterizedBy(BOOLEAN) to MemberName(extractorsPackage, "parseBooleanSetQueryParameter"),
        SET.parameterizedBy(BOOLEAN).copy(true) to MemberName(extractorsPackage, "parseOptionalBooleanSetQueryParameter"),
        UUID to MemberName(extractorsPackage, "parseUuidQueryParameter"),
        UUID.copy(true) to MemberName(extractorsPackage, "parseOptionalUuidQueryParameter"),
        LIST.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseUuidListQueryParameter"),
        LIST.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseOptionalUuidListQueryParameter"),
        SET.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseUuidSetQueryParameter"),
        SET.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseOptionalUuidSetQueryParameter"),
    )
}
