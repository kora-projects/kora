package ru.tinkoff.kora.http.server.common.handler

import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponseException
import java.util.*


@Throws(HttpServerResponseException::class)
fun extractStringPathParameter(request: HttpServerRequest, name: String): String {
    return request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractUUIDPathParameter(request: HttpServerRequest, name: String): UUID {
    var result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")

    result = result.trim()
    if (result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Path parameter '$name' has invalid blank string value")
    }

    return try {
        UUID.fromString(result)
    } catch (e: IllegalArgumentException) {
        throw HttpServerResponseException.of(400, "Path parameter '$name($result)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractIntPathParameter(request: HttpServerRequest, name: String): Int {
    var result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")

    result = result.trim()
    if (result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Path parameter '$name' has invalid blank string value")
    }

    return try {
        result.toInt()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter '$name($result)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractLongPathParameter(request: HttpServerRequest, name: String): Long {
    var result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")

    result = result.trim()
    if (result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Path parameter '$name' has invalid blank string value")
    }

    return try {
        result.toLong()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter '$name($result)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractDoublePathParameter(request: HttpServerRequest, name: String): Double {
    var result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")

    result = result.trim()
    if (result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Path parameter '$name' has invalid blank string value")
    }

    return try {
        result.toDouble()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Path parameter '$name($result)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumPathParameter(request: HttpServerRequest, enumType: Class<T>?, name: String): T {
    var result = request.pathParams()[name] ?: throw HttpServerResponseException.of(400, "Path parameter '$name' is required")

    result = result.trim()
    if (result.isEmpty()) {
        throw HttpServerResponseException.of(400, "Path parameter '$name' has invalid blank string value")
    }

    return try {
        java.lang.Enum.valueOf(enumType, result)
    } catch (exception: Exception) {
        throw HttpServerResponseException.of(400, "Path parameter '$name($result)' has invalid value")
    }
}

/*
 * Headers: String / List<String>
 */
@Throws(HttpServerResponseException::class)
fun extractStringHeaderParameter(request: HttpServerRequest, name: String): String {
    val result = request.headers()[name] ?: throw HttpServerResponseException.of(400, "Header '$name' is required")
    return result.joinToString(", ")
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringHeaderParameter(request: HttpServerRequest, name: String): String? {
    val result = request.headers()[name]
    return if (result.isNullOrEmpty()) null else result.joinToString(", ")
}

@Throws(HttpServerResponseException::class)
fun extractStringListHeaderParameter(request: HttpServerRequest, name: String): List<String> {
    return extractNullableStringListHeaderParameter(request, name) ?: throw HttpServerResponseException.of(400, "Header '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringListHeaderParameter(request: HttpServerRequest, name: String): List<String>? {
    val result = request.headers()[name] ?: return null

    return result
        .flatMap { str -> str.split(",") }
        .map { obj -> obj.trim { it <= ' ' } }
        .filter { it.isNotBlank() }
}

/*
 *  Query: String, Int, Long, Double, Boolean, Enum<?>, List<String>, List<Int>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
 */
@Throws(HttpServerResponseException::class)
fun extractStringQueryParameter(request: HttpServerRequest, name: String): String {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    }
    return result.iterator().next()
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringQueryParameter(request: HttpServerRequest, name: String): String? {
    val result = request.queryParams()[name]
    return if (result.isNullOrEmpty()) null else result.iterator().next()
}

@Throws(HttpServerResponseException::class)
fun extractStringListQueryParameter(request: HttpServerRequest, name: String): List<String> {
    val result = request.queryParams()[name] ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
    return if (result.isEmpty()) emptyList() else result.toMutableList().toList()
}

@Throws(HttpServerResponseException::class)
fun extractNullableStringListQueryParameter(request: HttpServerRequest, name: String): List<String>? {
    val result = request.queryParams()[name]
    return if (result.isNullOrEmpty()) null else result.toMutableList().toList()
}

@Throws(HttpServerResponseException::class)
fun extractUUIDQueryParameter(request: HttpServerRequest, name: String): UUID {
    return extractNullableUUIDQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableUUIDQueryParameter(request: HttpServerRequest, name: String): UUID? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        UUID.fromString(first)
    } catch (e: IllegalArgumentException) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractUUIDListQueryParameter(request: HttpServerRequest, name: String): List<UUID> {
    return extractNullableUUIDListQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableUUIDListQueryParameter(request: HttpServerRequest, name: String): List<UUID>? {
    val result = request.queryParams()[name]
    return if (result.isNullOrEmpty()) null else result
        .map {
            val first = it.trim()
            if (first.isEmpty()) {
                throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
            }

            try {
                UUID.fromString(first)
            } catch (e: IllegalArgumentException) {
                throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
            }
        }
        .toList()
}

@Throws(HttpServerResponseException::class)
fun extractIntQueryParameter(request: HttpServerRequest, name: String): Int {
    return extractNullableIntQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableIntQueryParameter(request: HttpServerRequest, name: String): Int? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        first.toInt()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractIntListQueryParameter(request: HttpServerRequest, name: String): List<Int> {
    return extractNullableIntListQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableIntListQueryParameter(request: HttpServerRequest, name: String): List<Int>? {
    val result = request.queryParams()[name] ?: return null
    return result.mapNotNull { v ->
        val first = v.trim()
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
        }

        try {
            first.toInt()
        } catch (e: NumberFormatException) {
            throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
        }
    }
}

@Throws(HttpServerResponseException::class)
fun extractLongQueryParameter(request: HttpServerRequest, name: String): Long {
    return extractNullableLongQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableLongQueryParameter(request: HttpServerRequest, name: String): Long? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        first.toLong()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractLongListQueryParameter(request: HttpServerRequest, name: String): List<Long> {
    return extractNullableLongListQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableLongListQueryParameter(request: HttpServerRequest, name: String): List<Long>? {
    val result = request.queryParams()[name] ?: return null
    return result.mapNotNull { v ->
        val first = v.trim()
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
        }

        try {
            first.toLong()
        } catch (e: NumberFormatException) {
            throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
        }
    }
}

@Throws(HttpServerResponseException::class)
fun extractDoubleQueryParameter(request: HttpServerRequest, name: String): Double {
    return extractNullableDoubleQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableDoubleQueryParameter(request: HttpServerRequest, name: String): Double? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        first.toDouble()
    } catch (e: NumberFormatException) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractDoubleListQueryParameter(request: HttpServerRequest, name: String): List<Double> {
    return extractNullableDoubleListQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableDoubleListQueryParameter(request: HttpServerRequest, name: String): List<Double>? {
    val result = request.queryParams()[name] ?: return null
    return result.mapNotNull { v ->
        val first = v.trim()
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
        }

        try {
            first.toDouble()
        } catch (e: NumberFormatException) {
            throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
        }
    }
}

@Throws(HttpServerResponseException::class)
fun extractBooleanQueryParameter(request: HttpServerRequest, name: String): Boolean {
    return extractNullableBooleanQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableBooleanQueryParameter(request: HttpServerRequest, name: String): Boolean? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        first.toBoolean()
    } catch (e: Exception) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun extractBooleanListQueryParameter(request: HttpServerRequest, name: String): List<Boolean> {
    return extractNullableBooleanListQueryParameter(request, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun extractNullableBooleanListQueryParameter(request: HttpServerRequest, name: String): List<Boolean>? {
    val result = request.queryParams()[name] ?: return null
    return result.mapNotNull { v ->
        val first = v.trim()
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
        }

        try {
            first.toBoolean()
        } catch (e: Exception) {
            throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
        }
    }
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumQueryParameter(request: HttpServerRequest, type: Class<T>?, name: String): T {
    return extractNullableEnumQueryParameter(request, type, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractNullableEnumQueryParameter(request: HttpServerRequest, type: Class<T>?, name: String): T? {
    val result = request.queryParams()[name]
    if (result.isNullOrEmpty()) {
        return null
    }

    val first = result.iterator().next().trim()
    if (first.isEmpty()) {
        throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
    }

    return try {
        java.lang.Enum.valueOf(type, first)
    } catch (exception: Exception) {
        throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
    }
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractEnumListQueryParameter(request: HttpServerRequest, type: Class<T>, name: String): List<T> {
    return extractNullableEnumListQueryParameter(request, type, name) ?: throw HttpServerResponseException.of(400, "Query parameter '$name' is required")
}

@Throws(HttpServerResponseException::class)
fun <T : Enum<T>?> extractNullableEnumListQueryParameter(request: HttpServerRequest, type: Class<T>, name: String): List<T>? {
    val result = request.queryParams()[name] ?: return null
    return result.mapNotNull { v ->
        val first = v.trim()
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '$name' has invalid blank string value")
        }

        try {
            java.lang.Enum.valueOf(type, v)
        } catch (e: Exception) {
            throw HttpServerResponseException.of(400, "Query parameter '$name($first)' has invalid value")
        }
    }
}
