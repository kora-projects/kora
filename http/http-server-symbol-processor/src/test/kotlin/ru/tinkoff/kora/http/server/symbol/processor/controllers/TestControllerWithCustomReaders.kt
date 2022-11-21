package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.Header
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController
open class TestControllerWithCustomReaders {
    @HttpRoute(method = HttpMethod.GET, path = "/test/{pathListEntity}")
    fun test(@Query("queryEntity") queryList: List<ReadableEntity>, @Path("pathListEntity") pathEntity: ReadableEntity, @Header("header-Entity") headerEntity: ReadableEntity? ): HttpServerResponseEntity<String> {
        val resultList = queryList + pathEntity

        return HttpServerResponseEntity(200, resultList.joinToString(", ") { it.string })
    }
}

