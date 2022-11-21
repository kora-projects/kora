package ru.tinkoff.kora.http.server.common.handler;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

public interface HttpServerRequestMapper<T> extends Mapping.MappingFunction {
    Mono<T> apply(HttpServerRequest request);
}
