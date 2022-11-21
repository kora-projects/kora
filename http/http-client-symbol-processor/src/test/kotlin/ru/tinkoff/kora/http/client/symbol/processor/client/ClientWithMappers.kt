package ru.tinkoff.kora.http.client.symbol.processor.client

import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.http.client.common.annotation.ResponseCodeMapper
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path
import java.nio.charset.StandardCharsets

@HttpClient
interface ClientWithMappers {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    @Mapping(value = ContributorListMapper::class)
    @Tag(ClientWithMappers::class)
    fun contributors(@Path("owner") owner: String?, @Path("repo") repo: String?): List<Contributor>

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    @ResponseCodeMapper(code = 418, mapper = CustomUnitMapper::class)
    @ResponseCodeMapper(code = 201, type = Unit::class)
    @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = CustomUnitMapper::class)
    fun createIssue(
        @Mapping(
            IssueRequestMapper::class
        ) @Tag(
            ClientWithMappers::class
        ) issue: Issue?, @Path("owner") owner: String?, @Path("repo") repo: String?
    )

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    @ResponseCodeMapper(code = 200)
    @ResponseCodeMapper(code = 201)
    fun otherCreateIssue(@Mapping(IssueRequestMapper::class) @Tag(ClientWithMappers::class) issue: Issue?, @Path("owner") owner: String?, @Path("repo") repo: String?)

    class ContributorListMapper : HttpClientResponseMapper<List<Contributor>, Mono<List<Contributor>>> {
        override fun apply(response: HttpClientResponse): Mono<List<Contributor>> {
            return Mono.just(listOf())
        }
    }

    class CustomUnitMapper : HttpClientResponseMapper<Unit, Mono<Unit>> {
        override fun apply(response: HttpClientResponse): Mono<Unit> {
            return Mono.empty()
        }
    }

    class IssueRequestMapper : HttpClientRequestMapper<Issue> {
        override fun apply(request: HttpClientRequestMapper.Request<Issue>): HttpClientRequestBuilder {
            return request.builder().body("TEST".toByteArray(StandardCharsets.UTF_8))
        }
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
