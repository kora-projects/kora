package ru.tinkoff.kora.http.client.common;

import javax.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public record HttpClientConfig(int connectTimeout, int readTimeout, @Nullable HttpClientProxyConfig proxy, @Nullable Boolean useEnvProxy) {

    public HttpClientConfig(
        @Nullable Duration connectTimeout,
        @Nullable Duration readTimeout,
        @Nullable HttpClientProxyConfig proxy,
        @Nullable Boolean useEnvProxy
    ) {
        this(
            connectTimeout != null ? (int) connectTimeout.toMillis() : 2000,
            readTimeout != null ? (int) readTimeout.toMillis() : 50000,
            proxy,
            useEnvProxy
        );
    }

    public record HttpClientProxyConfig(
        @Nullable String host,
        @Nullable Integer port,
        @Nullable List<String> nonProxyHosts,
        @Nullable String user,
        @Nullable String password
    ) {
        @Nullable
        public static HttpClientProxyConfig fromEnv() {
            String proxyString = System.getenv("https_proxy");
            proxyString = proxyString != null ? proxyString : System.getenv("HTTPS_PROXY");
            proxyString = proxyString != null ? proxyString : System.getenv("http_proxy");
            proxyString = proxyString != null ? proxyString : System.getenv("HTTP_PROXY");

            if (proxyString == null) {
                return null;
            }

            var uri = URI.create(proxyString);
            var host = uri.getHost();
            var port = uri.getPort();
            String user = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                var userInfo = uri.getUserInfo().split(":");
                user = userInfo[0];
                password = userInfo[1];
            }

            List<String> nonProxyHosts = null;
            var noProxyString = System.getenv("no_proxy");
            noProxyString = noProxyString != null ? noProxyString : System.getenv("NO_PROXY");

            if (noProxyString != null) {
                nonProxyHosts = List.of(noProxyString.split(","));
            }

            return new HttpClientProxyConfig(host, port, nonProxyHosts, user, password);
        }
    }
}
