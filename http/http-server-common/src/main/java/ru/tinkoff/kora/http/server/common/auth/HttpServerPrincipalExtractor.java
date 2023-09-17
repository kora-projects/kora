package ru.tinkoff.kora.http.server.common.auth;

import ru.tinkoff.kora.common.Principal;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import javax.annotation.Nullable;
import java.util.concurrent.CompletionStage;

public interface HttpServerPrincipalExtractor<T extends Principal> {
    CompletionStage<T> extract(HttpServerRequest request, @Nullable String value);
}
