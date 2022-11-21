package ru.tinkoff.kora.http.client.annotation.processor.client;


import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;

import java.util.List;

@HttpClient(configPath = "githubClientReactive", httpClientTag = GithubClientReactive.class, telemetryTag = GithubClientReactive.class)
public interface GithubClientReactive {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    Mono<List<Contributor>> contributors(@Path("owner") String owner, @Path("repo") String repo);

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    Mono<Void> createIssue(Issue issue, @Path("owner") String owner, @Path("repo") String repo);

    TypeReference<List<GithubClientReactive.Contributor>> contributorListTypeRef = new TypeReference<>() {};

    record Contributor(String login, int contributions) {}

    TypeReference<GithubClientReactive.Issue> issueTypeRef = new TypeReference<>() {};

    record Issue(
        String title,
        String body,
        List<String> assignees,
        int milestone,
        List<String> labels) {
    }
}
