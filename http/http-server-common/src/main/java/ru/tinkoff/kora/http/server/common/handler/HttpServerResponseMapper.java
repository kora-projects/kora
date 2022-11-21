package ru.tinkoff.kora.http.server.common.handler;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

public interface HttpServerResponseMapper<T> extends Mapping.MappingFunction {
    Mono<? extends HttpServerResponse> apply(T result);
}
