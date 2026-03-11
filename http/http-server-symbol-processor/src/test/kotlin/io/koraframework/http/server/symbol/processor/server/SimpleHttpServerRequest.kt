package io.koraframework.http.server.symbol.processor.server

import io.koraframework.http.common.body.HttpBody
import io.koraframework.http.common.body.HttpBodyInput
import io.koraframework.http.common.cookie.Cookie
import io.koraframework.http.common.cookie.Cookies
import io.koraframework.http.common.header.HttpHeaders
import io.koraframework.http.server.common.HttpServerRequest
import java.util.*

internal class SimpleHttpServerRequest(
    private val method: String,
    private val path: String,
    private val body: ByteArray,
    private val headers: HttpHeaders,
    private val routeParams: Map<String, String>
) : HttpServerRequest {
    override fun method(): String {
        return method
    }

    override fun path(): String {
        return path
    }

    override fun route(): String {
        return path
    }

    override fun headers(): HttpHeaders {
        return headers
    }

    override fun cookies(): MutableList<Cookie> {
        val cookies = arrayListOf<Cookie>()
        val header = headers.getAll("Cookie")
        Cookies.parseRequestCookies(500, true, header, cookies)
        return cookies
    }

    override fun queryParams(): Map<String, Deque<String>> {
        val questionMark = path.indexOf('?')
        if (questionMark < 0) {
            return mapOf()
        }
        val params = path.substring(questionMark + 1)
        val result = mutableMapOf<String, Deque<String>>()
        params.split("&".toRegex()).forEach { param ->
            val eq = param.indexOf('=')
            if (eq <= 0) {
                result[param] = ArrayDeque()
            } else {
                val name = param.substring(0, eq)
                val value = param.substring(eq + 1)
                result[name]?.add(value) ?: result.put(name, ArrayDeque<String>().apply { this.add(value) })
            }
        }
        return result
    }

    override fun pathParams(): Map<String, String> {
        return routeParams
    }

    override fun body(): HttpBodyInput {
        return HttpBody.octetStream(body)
    }
}
