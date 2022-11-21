package ru.tinkoff.kora.http.client.symbol.processor.client

import com.fasterxml.jackson.core.type.TypeReference
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path

@HttpClient(configPath = "githubClientReactive")
interface GithubClientReactive {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    fun contributors(@Path("owner") owner: String?, @Path("repo") repo: String?): Mono<List<Contributor>>

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    fun createIssue(issue: Issue?, @Path("owner") owner: String?, @Path("repo") repo: String?): Mono<Void>

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
