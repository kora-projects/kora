package ru.tinkoff.kora.http.client.ok;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;

import java.util.ArrayList;
import java.util.List;

public final class OkHttpClientWrapper implements Lifecycle, Wrapped<OkHttpClient> {
    private final OkHttpClientConfig config;
    private final HttpClientConfig baseConfig;
    private volatile OkHttpClient client;

    public OkHttpClientWrapper(OkHttpClientConfig config, HttpClientConfig baseConfig) {
        this.config = config;
        this.baseConfig = baseConfig;
    }

    @Override
    public void init() {
        var builder = new OkHttpClient.Builder()
            .connectTimeout(this.baseConfig.connectTimeout())
            .readTimeout(this.baseConfig.readTimeout())
            .followRedirects(this.config.followRedirects());

        List<Protocol> protocols = getProtocols(this.config.httpVersion());
        builder.protocols(protocols);

        var proxyConfig = this.baseConfig.proxy();
        if (this.baseConfig.useEnvProxy()) {
            proxyConfig = HttpClientConfig.HttpClientProxyConfig.fromEnv();
        }
        if (proxyConfig != null) {
            builder.proxySelector(new JdkProxySelector(proxyConfig));
            var proxyUser = proxyConfig.user();
            var proxyPassword = proxyConfig.password();
            if (proxyUser != null && proxyPassword != null) {
                builder.proxyAuthenticator(new ProxyAuthenticator(proxyUser, proxyPassword));
            }
        }
        this.client = builder.build();
    }

    private List<Protocol> getProtocols(OkHttpClientConfig.HttpVersion httpVersion) {
        return switch (httpVersion) {
            case HTTP_1_1 -> List.of(Protocol.HTTP_1_1);
            case HTTP_2 -> List.of(Protocol.HTTP_2, Protocol.HTTP_1_1);
            case HTTP_3 -> List.of(Protocol.HTTP_3, Protocol.HTTP_2, Protocol.HTTP_1_1);
        };
    }

    @Override
    public void release() {
        var client = this.client;
        this.client = null;
        if (client != null) {
            var pool = client.connectionPool();
            pool.evictAll();
        }
    }

    @Override
    public OkHttpClient value() {
        return this.client;
    }
}
