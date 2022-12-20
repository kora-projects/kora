package ru.tinkoff.kora.http.server.annotation.processor.controller;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

@HttpController
public class MultipleParamsController {
    public record Param1() {}

    public record Param2() {}

    public static final class Param1Mapping implements HttpServerRequestMapper<Param1> {

        @Override
        public Mono<Param1> apply(HttpServerRequest request) {
            if (request.headers().getFirst("Test-Header") == null) {
                throw HttpServerResponseException.of(null, 400, "TEST");
            }
            return Mono.just(new Param1());
        }
    }

    public static final class Param2Mapping implements HttpServerRequestMapper<Param2> {
        @Override
        public Mono<Param2> apply(HttpServerRequest request) {
            return Mono.just(new Param2());
        }
    }


    @HttpRoute(method = HttpMethod.POST, path = "/path")
    public void someMethodWithParam(@Mapping(Param1Mapping.class) Param1 param1, @Mapping(Param2Mapping.class) Param2 param2) {}


}
