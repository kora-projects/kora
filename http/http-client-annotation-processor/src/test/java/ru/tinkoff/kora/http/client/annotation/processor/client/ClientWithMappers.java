package ru.tinkoff.kora.http.client.annotation.processor.client;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.client.common.annotation.ResponseCodeMapper;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;

import java.nio.charset.StandardCharsets;
import java.util.List;

@HttpClient
public interface ClientWithMappers {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    @Mapping(value = ContributorListMapper.class)
    @Tag(ClientWithMappers.class)
    List<Contributor> contributors(@Path("owner") String owner, @Path("repo") String repo);

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    @ResponseCodeMapper(code = 418, mapper = CustomVoidMapper.class)
    @ResponseCodeMapper(code = 201, type = Void.class)
    @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = CustomVoidMapper.class)
    void createIssue(@Mapping(IssueRequestMapper.class) @Tag(ClientWithMappers.class) Issue issue, @Path("owner") String owner, @Path("repo") String repo);

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    @ResponseCodeMapper(code = 200)
    @ResponseCodeMapper(code = 201)
    void otherCreateIssue(@Mapping(IssueRequestMapper.class) @Tag(ClientWithMappers.class) Issue issue, @Path("owner") String owner, @Path("repo") String repo);

    class ContributorListMapper implements HttpClientResponseMapper<List<Contributor>, Mono<List<Contributor>>> {
        @Override
        public Mono<List<Contributor>> apply(HttpClientResponse response) {
            return Mono.just(List.of());
        }
    }

    class CustomVoidMapper implements HttpClientResponseMapper<Void, Mono<Void>> {
        @Override
        public Mono<Void> apply(HttpClientResponse response) {
            return Mono.empty();
        }
    }


    record Contributor(String login, int contributions) {}

    class IssueRequestMapper implements HttpClientRequestMapper<Issue> {

        @Override
        public HttpClientRequestBuilder apply(Request<Issue> request) {
            return request.builder().body("TEST".getBytes(StandardCharsets.UTF_8));
        }
    }

    record Issue(
        String title,
        String body,
        List<String> assignees,
        int milestone,
        List<String> labels) {
    }

}
