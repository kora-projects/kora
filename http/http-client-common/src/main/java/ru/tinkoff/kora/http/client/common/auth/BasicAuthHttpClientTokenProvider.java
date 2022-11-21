package ru.tinkoff.kora.http.client.common.auth;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthHttpClientTokenProvider implements HttpClientTokenProvider {
    private final String token;

    public BasicAuthHttpClientTokenProvider(String username, String password) {
        var usernameAndPassword = username + ":" + password;
        this.token= Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public Mono<String> getToken(HttpClientRequest request) {
        return Mono.just(this.token);
    }
}
