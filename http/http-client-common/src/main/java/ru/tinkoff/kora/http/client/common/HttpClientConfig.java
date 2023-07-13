package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface HttpClientConfig {
    default Duration connectTimeout() {
        return Duration.ofSeconds(2);
    }

    default Duration readTimeout() {
        return Duration.ofSeconds(50000);
    }

    @Nullable
    HttpClientProxyConfig proxy();

    default boolean useEnvProxy() {
        return false;
    }

    @ConfigValueExtractor
    interface HttpClientProxyConfig {

        String host();

        int port();

        @Nullable
        List<String> nonProxyHosts();

        @Nullable
        String user();

        @Nullable
        String password();

        @Nullable
        static HttpClientProxyConfig fromEnv() {
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

            return new $HttpClientConfig_HttpClientProxyConfig_ConfigValueExtractor.HttpClientProxyConfig_Impl(
                host, port, nonProxyHosts, user, password
            );
        }
    }
}
