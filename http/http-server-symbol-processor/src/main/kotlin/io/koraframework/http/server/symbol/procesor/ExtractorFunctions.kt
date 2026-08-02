package io.koraframework.http.server.symbol.procesor

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy


object ExtractorFunctions {
    private val extractorsPackage = ClassName("io.koraframework.http.server.common.request", "HttpRequestHandlerUtils")
    private val UUID = ClassName("java.util", "UUID")

    val path = mapOf<TypeName, MemberName>(
        BOOLEAN to MemberName(extractorsPackage, "parsePathBoolean"),
        STRING to MemberName(extractorsPackage, "parsePathString"),
        INT to MemberName(extractorsPackage, "parsePathInteger"),
        LONG to MemberName(extractorsPackage, "parsePathLong"),
        DOUBLE to MemberName(extractorsPackage, "parsePathDouble"),
        UUID to MemberName(extractorsPackage, "parsePathUuid"),
    )

    val header = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseHeaderString"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseHeaderStringNullable"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseHeaderStringList"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseHeaderStringListNullable"),
        SET.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseHeaderStringSet"),
        SET.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseHeaderStringSetNullable"),
        INT to MemberName(extractorsPackage, "parseHeaderInteger"),
        INT.copy(true) to MemberName(extractorsPackage, "parseHeaderIntegerNullable"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseHeaderIntegerList"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseHeaderIntegerListNullable"),
        SET.parameterizedBy(INT) to MemberName(extractorsPackage, "parseHeaderIntegerSet"),
        SET.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseHeaderIntegerSetNullable"),
        LONG to MemberName(extractorsPackage, "parseHeaderLong"),
        LONG.copy(true) to MemberName(extractorsPackage, "parseHeaderLongNullable"),
        LIST.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseHeaderLongList"),
        LIST.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseHeaderLongListNullable"),
        SET.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseHeaderLongSet"),
        SET.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseHeaderLongSetNullable"),
        DOUBLE to MemberName(extractorsPackage, "parseHeaderDouble"),
        DOUBLE.copy(true) to MemberName(extractorsPackage, "parseHeaderDoubleNullable"),
        LIST.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseHeaderDoubleList"),
        LIST.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseHeaderDoubleListNullable"),
        SET.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseHeaderDoubleSet"),
        SET.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseHeaderDoubleSetNullable"),
        UUID to MemberName(extractorsPackage, "parseHeaderUuid"),
        UUID.copy(true) to MemberName(extractorsPackage, "parseHeaderUuidNullable"),
        LIST.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseHeaderUuidList"),
        LIST.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseHeaderUuidListNullable"),
        SET.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseHeaderUuidSet"),
        SET.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseHeaderUuidSetNullable"),
    )

    val cookie = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseCookieString"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseCookieStringNullable"),
        ClassName("io.koraframework.http.common.cookie", "Cookie") to MemberName(extractorsPackage, "parseCookie"),
        ClassName("io.koraframework.http.common.cookie", "Cookie").copy(true) to MemberName(extractorsPackage, "parseCookieNullable"),
    )

    val query = mapOf<TypeName, MemberName>(
        STRING to MemberName(extractorsPackage, "parseQueryString"),
        STRING.copy(true) to MemberName(extractorsPackage, "parseQueryStringNullable"),
        LIST.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseQueryStringList"),
        LIST.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseQueryStringListNullable"),
        SET.parameterizedBy(STRING) to MemberName(extractorsPackage, "parseQueryStringSet"),
        SET.parameterizedBy(STRING).copy(true) to MemberName(extractorsPackage, "parseQueryStringSetNullable"),
        INT to MemberName(extractorsPackage, "parseQueryInteger"),
        INT.copy(true) to MemberName(extractorsPackage, "parseQueryIntegerNullable"),
        LIST.parameterizedBy(INT) to MemberName(extractorsPackage, "parseQueryIntegerList"),
        LIST.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseQueryIntegerListNullable"),
        SET.parameterizedBy(INT) to MemberName(extractorsPackage, "parseQueryIntegerSet"),
        SET.parameterizedBy(INT).copy(true) to MemberName(extractorsPackage, "parseQueryIntegerSetNullable"),
        LONG to MemberName(extractorsPackage, "parseQueryLong"),
        LONG.copy(true) to MemberName(extractorsPackage, "parseQueryLongNullable"),
        LIST.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseQueryLongList"),
        LIST.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseQueryLongListNullable"),
        SET.parameterizedBy(LONG) to MemberName(extractorsPackage, "parseQueryLongSet"),
        SET.parameterizedBy(LONG).copy(true) to MemberName(extractorsPackage, "parseQueryLongSetNullable"),
        DOUBLE to MemberName(extractorsPackage, "parseQueryDouble"),
        DOUBLE.copy(true) to MemberName(extractorsPackage, "parseQueryDoubleNullable"),
        LIST.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseQueryDoubleList"),
        LIST.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseQueryDoubleListNullable"),
        SET.parameterizedBy(DOUBLE) to MemberName(extractorsPackage, "parseQueryDoubleSet"),
        SET.parameterizedBy(DOUBLE).copy(true) to MemberName(extractorsPackage, "parseQueryDoubleSetNullable"),
        BOOLEAN to MemberName(extractorsPackage, "parseQueryBoolean"),
        BOOLEAN.copy(true) to MemberName(extractorsPackage, "parseQueryBooleanNullable"),
        LIST.parameterizedBy(BOOLEAN) to MemberName(extractorsPackage, "parseQueryBooleanList"),
        LIST.parameterizedBy(BOOLEAN).copy(true) to MemberName(extractorsPackage, "parseQueryBooleanListNullable"),
        SET.parameterizedBy(BOOLEAN) to MemberName(extractorsPackage, "parseQueryBooleanSet"),
        SET.parameterizedBy(BOOLEAN).copy(true) to MemberName(extractorsPackage, "parseQueryBooleanSetNullable"),
        UUID to MemberName(extractorsPackage, "parseQueryUuid"),
        UUID.copy(true) to MemberName(extractorsPackage, "parseQueryUuidNullable"),
        LIST.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseQueryUuidList"),
        LIST.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseQueryUuidListNullable"),
        SET.parameterizedBy(UUID) to MemberName(extractorsPackage, "parseQueryUuidSet"),
        SET.parameterizedBy(UUID).copy(true) to MemberName(extractorsPackage, "parseQueryUuidSetNullable"),
    )
}
