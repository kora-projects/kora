package ru.tinkoff.kora.http.client.ok;

import okhttp3.OkHttpClient;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;

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
