package ru.tinkoff.kora.http.client.common.auth;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthHttpClientTokenProvider implements HttpClientTokenProvider {

    @Nullable
    private final String token;

    public BasicAuthHttpClientTokenProvider(@Nullable String username, @Nullable String password) {
        if (username == null || password == null) {
            this.token = null;
        } else {
            var usernameAndPassword = username + ":" + password;
            this.token = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.US_ASCII));
        }
    }

    @Override
    @Nullable
    public String getToken(HttpClientRequest request) {
        return this.token;
    }
}
