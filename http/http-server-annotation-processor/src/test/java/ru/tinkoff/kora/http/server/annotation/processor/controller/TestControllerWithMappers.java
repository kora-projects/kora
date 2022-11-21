package ru.tinkoff.kora.http.server.annotation.processor.controller;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerWithMappers {

    @HttpRoute(method = GET, path = "/queryString")
    @Mapping(StringResponseMapper.class)
    String queryString(@Mapping(StringRequestMapper.class) String value) {
        return value;
    }

    @HttpRoute(method = GET, path = "/queryNullableString")
    @Tag(TestControllerWithMappers.class)
    String queryNullableString(@Mapping(StringRequestMapper.class) @Tag(TestControllerWithMappers.class) String value) {
        return new String(value);
    }

    public static class StringRequestMapper implements HttpServerRequestMapper<String> {
        @Override
        public Mono<String> apply(HttpServerRequest request) {
            return Mono.just("test");
        }
    }

    public static class StringResponseMapper implements HttpServerResponseMapper<String> {
        @Override
        public Mono<? extends HttpServerResponse> apply(String result) {
            return Mono.just(new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), UTF_8.encode(result)));
        }
    }

    public static class ByteArrayResponseMapper implements HttpServerResponseMapper<String> {
        @Override
        public Mono<? extends HttpServerResponse> apply(String result) {
            return Mono.just(new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), UTF_8.encode(result)));
        }
    }
}
