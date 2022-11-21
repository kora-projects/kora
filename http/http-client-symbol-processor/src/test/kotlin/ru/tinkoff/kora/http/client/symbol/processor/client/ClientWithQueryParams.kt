package ru.tinkoff.kora.http.client.symbol.processor.client

import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Query
import java.time.LocalDate

@HttpClient(configPath = "clientWithQueryParams")
interface ClientWithQueryParams {
    @HttpRoute(method = HttpMethod.POST, path = "/test1?test=test")
    fun test1(@Query test1: String?)

    @HttpRoute(method = HttpMethod.POST, path = "/test2?")
    fun test2(@Query("test2") test: String)

    @HttpRoute(method = HttpMethod.POST, path = "/test3")
    fun test3(@Query("test3") test: String)

    @HttpRoute(method = HttpMethod.POST, path = "/test4")
    fun test4(@Query("test4") test4: String?, @Query("test") test: String?)

    @HttpRoute(method = HttpMethod.POST, path = "/test5")
    fun test5(
        @Query test51: String?,
        @Query test52: String?,
        @Query test53: String?,
        @Query test54: String?,
        @Query test55: String?,
        @Query test56: String?
    )

    @HttpRoute(method = HttpMethod.POST, path = "/test6")
    fun test6(@Query test61: String?, @Query test62: String?, @Query test63: String?)

    @HttpRoute(method = HttpMethod.POST, path = "/nonStringParams")
    fun nonStringParams(@Query query1: Int, @Query query2: LocalDate, @Query query3: Int?)

    @HttpRoute(method = HttpMethod.POST, path = "/multipleParams")
    fun multipleParams(@Query query1: List<Int>, @Query query2: Iterable<String>?, @Query query3: List<LocalDate?>)
}
