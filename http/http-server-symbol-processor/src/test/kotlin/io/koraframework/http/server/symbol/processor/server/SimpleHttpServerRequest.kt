package io.koraframework.http.server.symbol.processor.server

import io.koraframework.http.common.body.HttpBody
import io.koraframework.http.common.body.HttpBodyInput
import io.koraframework.http.common.cookie.Cookie
import io.koraframework.http.common.cookie.Cookies
import io.koraframework.http.common.header.HttpHeaders
import io.koraframework.http.server.common.request.HttpServerRequest
import java.util.*
import kotlin.collections.forEach

internal class SimpleHttpServerRequest(
    private val method: String,
    private val path: String,
    private val body: ByteArray,
    private val headers: HttpHeaders,
    private val routeParams: Map<String, String>
) : HttpServerRequest {

    override fun scheme(): String = "http"

    override fun host(): String = "localhost"

    override fun method(): String = method

    override fun path(): String = path

    override fun pathTemplate(): String = path

    override fun headers(): HttpHeaders = headers

    override fun cookies(): MutableList<Cookie> {
        val cookies = arrayListOf<Cookie>()
        val header = headers.getAll("Cookie")
        Cookies.parseRequestCookies(500, true, header, cookies)
        return cookies
    }

    override fun queryParams(): Map<String, List<String>> {
        val questionMark = path.indexOf('?')
        if (questionMark < 0) {
            return mapOf()
        }
        val params = path.substring(questionMark + 1)
        val result = mutableMapOf<String, MutableList<String>>()
        params.split("&".toRegex()).forEach { param ->
            val eq = param.indexOf('=')
            if (eq <= 0) {
                result[param] = arrayListOf()
            } else {
                val name = param.substring(0, eq)
                val value = param.substring(eq + 1)
                result[name]?.add(value) ?: result.put(name, ArrayList<String>().apply { this.add(value) })
            }
        }
        return result
    }

    override fun pathParams(): Map<String, String> = routeParams

    override fun body(): HttpBodyInput = HttpBody.octetStream(body)

    override fun requestStartTimeInNanos(): Long = 0L
}
