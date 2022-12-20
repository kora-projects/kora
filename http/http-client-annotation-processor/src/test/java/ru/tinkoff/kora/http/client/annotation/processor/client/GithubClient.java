package ru.tinkoff.kora.http.client.annotation.processor.client;


import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.InterceptWith;
import ru.tinkoff.kora.http.common.annotation.Path;

import java.util.List;
import java.util.function.Function;

@HttpClient
@InterceptWith(GithubClient.TestInterceptor1.class)
@InterceptWith(value = GithubClient.TestInterceptor1.class, tag = @Tag(GithubClient.TestInterceptor1.class))
public interface GithubClient {
    class TestInterceptor1 implements HttpClientInterceptor {

        @Override
        public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
            return chain.apply(request);
        }
    }

    class TestInterceptor2 implements HttpClientInterceptor {

        @Override
        public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
            return chain.apply(request);
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    @InterceptWith(GithubClient.TestInterceptor1.class)
    @InterceptWith(GithubClient.TestInterceptor2.class)
    @InterceptWith(GithubClient.TestInterceptor2.class)
    @InterceptWith(value = GithubClient.TestInterceptor2.class, tag = @Tag(GithubClient.TestInterceptor2.class))
    List<Contributor> contributors(@Path("owner") String owner, @Path("repo") String repo);

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    void createIssue(Issue issue, @Path("owner") String owner, @Path("repo") String repo);

    TypeReference<List<Contributor>> contributorListTypeRef = new TypeReference<>() {};

    record Contributor(String login, int contributions) {}

    TypeReference<Issue> issueTypeRef = new TypeReference<>() {};

    record Issue(
        String title,
        String body,
        List<String> assignees,
        int milestone,
        List<String> labels) {
    }
}
