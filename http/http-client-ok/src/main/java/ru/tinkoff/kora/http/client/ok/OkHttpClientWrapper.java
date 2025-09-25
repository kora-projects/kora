package ru.tinkoff.kora.http.client.ok;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;

import java.util.List;

public final class OkHttpClientWrapper implements Lifecycle, Wrapped<OkHttpClient> {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpClientWrapper.class);

    private final OkHttpClientConfig config;
    private final HttpClientConfig baseConfig;
    private final All<OkHttpConfigurer> configurers;
    private volatile OkHttpClient client;

    public OkHttpClientWrapper(OkHttpClientConfig config, HttpClientConfig baseConfig, All<OkHttpConfigurer> configurers) {
        this.config = config;
        this.baseConfig = baseConfig;
        this.configurers = configurers;
    }

    @Override
    public void init() {
        logger.debug("OkHttpClient starting...");
        var started = System.nanoTime();

        var builder = new OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(this.baseConfig.connectTimeout())
            .readTimeout(this.baseConfig.readTimeout())
            .followRedirects(this.config.followRedirects());

        var protocols = getProtocols(this.config.httpVersion());
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
        for (var configurer : this.configurers) {
            builder = configurer.configure(builder);
        }
        this.client = builder.build();

        logger.info("OkHttpClient started in {}", TimeUtils.tookForLogging(started));
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
            logger.debug("OkHttpClient stopping...");
            var started = System.nanoTime();

            var pool = client.connectionPool();
            pool.evictAll();

            logger.info("OkHttpClient stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public OkHttpClient value() {
        return this.client;
    }
}
