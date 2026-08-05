package io.koraframework.http.client.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigMapper
public interface HttpClientConfig {

    /**
     * @return Maximum time to establish a connection.
     */
    default Duration connectTimeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * @return Maximum time to read a response.
     */
    default Duration readTimeout() {
        return Duration.ofMinutes(2);
    }

    /**
     * @return Proxy settings used for outgoing requests.
     */
    @Nullable
    HttpClientProxyConfig proxy();

    /**
     * @return Whether to use https_proxy / HTTPS_PROXY / http_proxy / HTTP_PROXY and no_proxy / NO_PROXY environment variables for proxy configuration.
     */
    default boolean useEnvProxy() {
        return false;
    }

    @ConfigMapper
    interface HttpClientProxyConfig {

        /**
         * @return Proxy host.
         */
        String host();

        /**
         * @return Proxy port.
         */
        int port();

        /**
         * @return Hosts to exclude from proxying.
         */
        @Nullable
        List<String> nonProxyHosts();

        /**
         * @return Proxy user.
         */
        @Nullable
        String user();

        /**
         * @return Proxy password.
         */
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

            return new $HttpClientConfig_HttpClientProxyConfig_ConfigValueMapper.HttpClientProxyConfig_Impl(
                host, port, nonProxyHosts, user, password
            );
        }
    }
}
