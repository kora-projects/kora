package ru.tinkoff.kora.http.client.common.auth;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import java.util.concurrent.CompletionStage;

public interface HttpClientTokenProvider {
    CompletionStage<String> getToken(HttpClientRequest request);
}
