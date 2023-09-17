package ru.tinkoff.kora.http.server.symbol.procesor

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*


object ExtractorFunctions {
    private val extractorsPackage = ClassName("ru.tinkoff.kora.http.server.common.handler", "RequestHandlerUtils")
    private val uuid = UUID::class.asClassName()

    val path = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseStringPathParameter"),
        uuid to MemberName(extractorsPackage, "parseUUIDPathParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerPathParameter"),
        LONG to MemberName(extractorsPackage, "parseLongPathParameter"),
        DOUBLE to MemberName(extractorsPackage, "parseDoublePathParameter"),
    )

    val header = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseStringHeaderParameter"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseOptionalStringHeaderParameter"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringListHeaderParameter"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringListHeaderParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerHeaderParameter"),
        INT.copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerHeaderParameter"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerListHeaderParameter"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerListHeaderParameter"),
    )

    val query = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseStringQueryParameter"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseOptionalStringQueryParameter"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseStringListQueryParameter"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseOptionalStringListQueryParameter"),
        INT to MemberName(extractorsPackage, "parseIntegerQueryParameter"),
        INT.copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerQueryParameter"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseIntegerListQueryParameter"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseOptionalIntegerListQueryParameter"),
        LONG to MemberName(extractorsPackage, "parseLongQueryParameter"),
        LONG.copy(true) to MemberName(extractorsPackage, "parseOptionalLongQueryParameter"),
        LIST.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseLongListQueryParameter"),
        LIST.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseOptionalLongListQueryParameter"),
        DOUBLE to MemberName(extractorsPackage, "parseDoubleQueryParameter"),
        DOUBLE.copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleQueryParameter"),
        LIST.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseDoubleListQueryParameter"),
        LIST.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseOptionalDoubleListQueryParameter"),
        BOOLEAN to MemberName(extractorsPackage, "parseBooleanQueryParameter"),
        BOOLEAN.copy(true) to MemberName(extractorsPackage, "parseOptionalBooleanQueryParameter"),
        LIST.parameterizedBy(BOOLEAN) to MemberName(extractorsPackage, "parseBooleanListQueryParameter"),
        LIST.parameterizedBy(BOOLEAN).copy(true) to MemberName(extractorsPackage, "parseOptionalBooleanListQueryParameter"),
        uuid to MemberName(extractorsPackage, "parseUuidQueryParameter"),
        uuid.copy(true) to MemberName(extractorsPackage, "parseOptionalUuidQueryParameter"),
        LIST.parameterizedBy(uuid) to MemberName(extractorsPackage, "parseUuidListQueryParameter"),
        LIST.parameterizedBy(uuid).copy(true) to MemberName(extractorsPackage, "parseOptionalUuidListQueryParameter"),

        )
}
