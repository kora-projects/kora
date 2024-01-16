package ru.tinkoff.kora.http.client.ok;

import jakarta.annotation.Nullable;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Proxy;

final class ProxyAuthenticator implements Authenticator {
    private final String proxyUser;
    private final String proxyPassword;

    public ProxyAuthenticator(String proxyUser, String proxyPassword) {
        this.proxyUser = proxyUser;
        this.proxyPassword = proxyPassword;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NotNull Response response) throws IOException {

        var challenges = response.challenges();
        var request = response.request();
        if (response.code() != 407) {
            return null;
        }
        if (route == null || route.proxy() == Proxy.NO_PROXY) {
            return null;
        }
        for (var challenge : challenges) {
            if (!"Basic".equalsIgnoreCase(challenge.scheme())) {
                continue;
            }
            var credential = Credentials.basic(proxyUser, proxyPassword);
            return request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
        }

        return null; // No challenges were satisfied!
    }
}
