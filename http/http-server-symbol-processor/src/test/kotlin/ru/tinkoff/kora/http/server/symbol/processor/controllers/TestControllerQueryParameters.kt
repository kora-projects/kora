package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import java.util.*

@HttpController
open class TestControllerQueryParameters {
    /*
    Query: String, Integer, Long, Double, Boolean, Enum<?>, List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
     */
    @HttpRoute(method = HttpMethod.GET, path = "/queryString")
    fun queryString(@Query("value") value1: String?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableString")
    fun queryNullableString(@Query value: String?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryStringList")
    fun queryStringList(@Query value: List<String?>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryInteger")
    fun queryInteger(@Query value: Int) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryIntegerObject")
    fun queryIntegerObject(@Query value: Int?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableInteger")
    fun queryNullableInteger(@Query value: Int?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryIntegerList")
    fun queryIntegerList(@Query value: List<Int?>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryLong")
    fun queryLong(@Query value: Long) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryLongObject")
    fun queryLongObject(@Query value: Long?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableLong")
    fun queryNullableLong(@Query value: Long?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryLongList")
    fun queryLongList(@Query value: List<Long?>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryDouble")
    fun queryDouble(@Query value: Double) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryDoubleObject")
    fun queryDoubleObject(@Query value: Double?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableDouble")
    fun queryNullableDouble(@Query value: Double?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryDoubleList")
    fun queryDoubleList(@Query value: List<Double?>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryBoolean")
    fun queryBoolean(@Query value: Boolean) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryBooleanObject")
    fun queryBooleanObject(@Query value: Boolean?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableBoolean")
    fun queryNullableBoolean(@Query value: Boolean?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryBooleanList")
    fun queryBooleanList(@Query value: List<Boolean?>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryEnum")
    fun queryEnum(@Query value: TestEnum?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableEnum")
    fun queryNullableEnum(@Query value: TestEnum?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryEnumList")
    fun queryEnumList(@Query value: List<TestEnum?>?) {
    }
}
