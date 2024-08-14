package ru.tinkoff.kora.http.client.common.auth;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BasicAuthHttpClientTokenProvider implements HttpClientTokenProvider {

    private final CompletableFuture<String> tokenFuture;

    public BasicAuthHttpClientTokenProvider(String username, String password) {
        var usernameAndPassword = username + ":" + password;
        var token = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.US_ASCII));
        this.tokenFuture = CompletableFuture.completedFuture(token);
    }

    @Override
    public CompletionStage<String> getToken(HttpClientRequest request) {
        request.toBuilder()
        return this.tokenFuture;
    }
}
