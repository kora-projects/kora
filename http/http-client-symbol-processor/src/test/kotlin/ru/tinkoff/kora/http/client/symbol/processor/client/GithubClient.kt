package ru.tinkoff.kora.http.client.symbol.processor.client

import com.fasterxml.jackson.core.type.TypeReference
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.*
import java.util.function.Function

@HttpClient(telemetryTag = [GithubClient::class], httpClientTag = [GithubClient::class])
@InterceptWith(GithubClient.TestInterceptor1::class)
@InterceptWith(value = GithubClient.TestInterceptor1::class, tag = Tag(GithubClient.TestInterceptor1::class))
interface GithubClient {
    open class TestInterceptor1 : HttpClientInterceptor {
        override fun processRequest(chain: Function<HttpClientRequest, Mono<HttpClientResponse>>, request: HttpClientRequest): Mono<HttpClientResponse> {
            return chain.apply(request)
        }
    }

    open class TestInterceptor2 : HttpClientInterceptor {
        override fun processRequest(chain: Function<HttpClientRequest, Mono<HttpClientResponse>>, request: HttpClientRequest): Mono<HttpClientResponse> {
            return chain.apply(request)
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    @InterceptWith(TestInterceptor1::class)
    @InterceptWith(TestInterceptor2::class)
    @InterceptWith(TestInterceptor2::class)
    @InterceptWith(
        value = TestInterceptor2::class,
        tag = Tag(TestInterceptor2::class)
    )
    fun contributors(@Path("owner") owner: String?, @Path("repo") repo: String?): List<Contributor>

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    fun createIssue(issue: Issue?, @Path("owner") owner: String, @Path("repo") repo: String?)

    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/issues/find")
    fun findIssue( @Path("owner") owner: String, @Path("repo") repo: String?, @Header("header1") header1: String?, @Header("header2") header2: Int, @Query("query1") query1: String?, @Query("query2") query2: Int )

    companion object {
        val contributorListTypeRef: TypeReference<List<Contributor>> = object : TypeReference<List<Contributor>>() {}
        val issueTypeRef: TypeReference<Issue> = object : TypeReference<Issue>() {}
    }

    data class Issue(
        val title: String,
        val body: String,
        val assignees: List<String>,
        val milestone: Int,
        val labels: List<String>
    )
    data class Contributor(val login: String, val contributions: Int)
}
