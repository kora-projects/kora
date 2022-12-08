package ru.tinkoff.kora.http.server.common.handler

import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponseException
import java.util.*
import java.util.stream.Collectors


@Throws(HttpServerResponseException::class)
fun extractStringPathParameter(request: HttpServerRequest, name: String): String {
    return request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
}

@Throws(HttpServerResponseException::class)
fun extractUUIDPathParameter(request: HttpServerRequest, name: String): UUID {
    val result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
    return try {
        UUID.fromString(result)
    } catch (e: IllegalArgumentException) {
        throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value '%s'".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun extractIntPathParameter(request: HttpServerRequest, name: String): Int {
    val result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
    return try {
        result.toInt()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value '%s'".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun extractLongPathParameter(request: HttpServerRequest, name: String): Long {
    val result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
    return try {
        result.toLong()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun extractDoublePathParameter(request: HttpServerRequest, name: String): Double {
    val result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
    return try {
        result.toDouble()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumPathParameter(request: HttpServerRequest, enumType: Class<T>?, name: String): T {
    val result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '%s' is required".format(name))
    return try {
        java.lang.Enum.valueOf(enumType, result)
    } catch (exception: Exception) {
        throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".format(name, result))
    }
}

/*
     * Headers: String / List<String>
     */

/*
     * Headers: String / List<String>
     */
@Throws(HttpServerResponseException::class)
fun extractStringHeaderParameter(request: HttpServerRequest, name: String): String {
    val result = request.headers()[name] ?: throw HttpServerResponseException.of(400, "Header '%s' is required".format(name))
    return result.joinToString(", ")
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringHeaderParameter(request: HttpServerRequest, name: String): String? {
    val result = request.headers()[name]
    return if (result == null || result.isEmpty()) {
        null
    } else result.joinToString(", ")
}

@Throws(HttpServerResponseException::class)
fun extractStringListHeaderParameter(request: HttpServerRequest, name: String): List<String> {
    val result = request.headers()[name] ?: throw HttpServerResponseException.of(400, "Header '%s' is required".format(name))
    return result
        .flatMap { str -> str.split(",") }
        .map { obj -> obj.trim { it <= ' ' } }
        .filter { it.isNotBlank() }
}
/*
        Query: String, Int, Long, Double, Boolean, Enum<?>, List<String>, List<Int>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
     */

/*
        Query: String, Int, Long, Double, Boolean, Enum<?>, List<String>, List<Int>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
     */
@Throws(HttpServerResponseException::class)
fun extractStringQueryParameter(request: HttpServerRequest, name: String): String {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    return result.iterator().next()
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringQueryParameter(request: HttpServerRequest, name: String): String? {
    val result = request.queryParams()[name]
    return if (result == null || result.isEmpty()) {
        null
    } else result.iterator().next()
}

@Throws(HttpServerResponseException::class)
fun extractStringListQueryParameter(request: HttpServerRequest, name: String): List<String> {
    val queryParams = request.queryParams()
    val result = queryParams[name] ?: return emptyList()
    return result.toMutableList().toList()
}


@Throws(HttpServerResponseException::class)
fun extractUUIDQueryParameter(request: HttpServerRequest, name: String): UUID {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    val first = result.iterator().next()
    return try {
        UUID.fromString(first)
    } catch (e: IllegalArgumentException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
    }
}


@Throws(HttpServerResponseException::class)
fun extractIntQueryParameter(request: HttpServerRequest, name: String): Int {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    val first = result.iterator().next()
    return try {
        first.toInt()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
    }
}

@Throws(HttpServerResponseException::class)
fun extractNullableIntQueryParameter(request: HttpServerRequest, name: String): Int? {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        return null
    }
    val first = result.iterator().next()
    return try {
        first.toInt()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
    }
}

@Throws(HttpServerResponseException::class)
fun extractIntListQueryParameter(request: HttpServerRequest, name: String): List<Int> {
    val result = request.queryParams()[name] ?: return emptyList()
    return result
        .mapNotNull { v ->
            try {
                v.toInt()
            } catch (e: NumberFormatException) {
                throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, v))
            }
        }
}


@Throws(HttpServerResponseException::class)
fun extractLongQueryParameter(request: HttpServerRequest, name: String): Long {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    val first = result.iterator().next()
    return try {
        first.toLong()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
    }
}

@Throws(HttpServerResponseException::class)
fun extractNullableLongQueryParameter(request: HttpServerRequest, name: String): Long? {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        return null
    }
    val first = result.iterator().next()
    return try {
        first.toLong()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
    }
}

@Throws(HttpServerResponseException::class)
fun extractLongListQueryParameter(request: HttpServerRequest, name: String): List<Long?>? {
    val result = request.queryParams()[name] ?: return java.util.List.of()
    return result.stream()
        .map { v: String? ->
            if (v == null || v.isBlank()) {
                return@map null
            }
            try {
                return@map v.toLong()
            } catch (e: NumberFormatException) {
                throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, v))
            }
        }
        .collect(Collectors.toList())
}


@Throws(HttpServerResponseException::class)
fun extractDoubleQueryParameter(request: HttpServerRequest, name: String): Double {
    val result = request.queryParams()[name]?.first() ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    return try {
        result.toDouble()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun extractNullableDoubleQueryParameter(request: HttpServerRequest, name: String): Double? {
    val result = request.queryParams()[name]?.first()
    return try {
        result?.toDouble()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun extractDoubleListQueryParameter(request: HttpServerRequest, name: String): List<Double> {
    return request.queryParams()[name]?.mapNotNull { v: String? ->
        try {
            v?.toDouble()
        } catch (e: NumberFormatException) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, v))
        }
    } ?: return emptyList()
}


@Throws(HttpServerResponseException::class)
fun extractBooleanQueryParameter(request: HttpServerRequest, name: String): Boolean {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    val first = result.iterator().next()
    return if (first in listOf("true", "false")) {
        first.toBoolean()
    } else throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
}

@Throws(HttpServerResponseException::class)
fun extractNullableBooleanQueryParameter(request: HttpServerRequest, name: String): Boolean? {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        return null
    }
    val first = result.iterator().next()
    return if (first in listOf("true", "false")) {
        first.toBoolean()
    } else throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, first))
}

@Throws(HttpServerResponseException::class)
fun extractBooleanListQueryParameter(request: HttpServerRequest, name: String): List<Boolean?>? {
    val result = request.queryParams()[name] ?: return emptyList()
    return result
        .map { v ->
            if (v == null || v.isBlank()) {
                return@map null
            }
            return@map if (v in listOf("true", "false")) {
                v.toBoolean()
            } else throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, v))
        }
}


@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumQueryParameter(request: HttpServerRequest, type: Class<T>?, name: String): T {
    return extractNullableEnumQueryParameter(request, type, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractNullableEnumQueryParameter(request: HttpServerRequest, type: Class<T>?, name: String): T? {
    val result = request.queryParams()[name]
    if (result == null || result.isEmpty()) {
        return null
    }
    val first = result.iterator().next()
    return try {
        java.lang.Enum.valueOf(type, first)
    } catch (exception: Exception) {
        throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, result))
    }
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumListQueryParameter(request: HttpServerRequest, type: Class<T>, name: String): List<T> {
    val result = request.queryParams()[name] ?: return emptyList()
    return result
        .mapNotNull { v: String? ->
            try {
                java.lang.Enum.valueOf(type, v)
            } catch (exception: Exception) {
                throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".format(name, result))
            }
        }
}
