package ru.tinkoff.kora.http.server.common.auth;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Principal;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import javax.annotation.Nullable;

public interface HttpServerPrincipalExtractor<T extends Principal> {
    Mono<T> extract(HttpServerRequest request,  @Nullable String value);
}
