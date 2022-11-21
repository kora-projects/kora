package ru.tinkoff.kora.http.client.symbol.processor.client

import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path

@HttpClient
interface GithubClientKotlin {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    suspend fun contributors(@Path("owner") owner: String, @Path("repo") repo: String): List<GithubClient.Contributor>

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    suspend fun createIssue(issue: GithubClient.Issue, @Path("owner") owner: String, @Path("repo") repo: String)
}
