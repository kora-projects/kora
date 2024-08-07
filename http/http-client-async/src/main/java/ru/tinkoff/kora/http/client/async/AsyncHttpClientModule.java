package ru.tinkoff.kora.http.client.async;

import io.netty.channel.EventLoopGroup;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.channel.ChannelPool;
import org.asynchttpclient.proxy.ProxyServer;
import org.asynchttpclient.proxy.ProxyType;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientModule;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

public interface AsyncHttpClientModule extends NettyCommonModule, HttpClientModule {

    default AsyncHttpClientConfig asyncHttpClientConfig(Config config, ConfigValueExtractor<AsyncHttpClientConfig> extractor) {
        return extractor.extract(config.get("httpClient.async"));
    }

    default org.asynchttpclient.AsyncHttpClientConfig nettyClientConfig(@Tag(WorkerLoopGroup.class) EventLoopGroup eventLoopGroup, AsyncHttpClientConfig asyncConfig, HttpClientConfig config) {
        DefaultAsyncHttpClientConfig.Builder builder = Dsl.config()
            .setEventLoopGroup(eventLoopGroup)
            .setFollowRedirect(asyncConfig.followRedirects())
            .setConnectTimeout((int) config.connectTimeout().toMillis())
            .setReadTimeout((int) config.readTimeout().toMillis());

        HttpClientConfig.HttpClientProxyConfig proxy = config.proxy();
        if (config.useEnvProxy()) {
            proxy = HttpClientConfig.HttpClientProxyConfig.fromEnv();
        }
        if (proxy != null) {
            ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(proxy.host(), proxy.port())
                .setProxyType(ProxyType.HTTP);

            if (proxy.nonProxyHosts() != null) {
                proxyBuilder.setNonProxyHosts(proxy.nonProxyHosts());
            }

            if (proxy.user() != null && proxy.password() != null) {
                proxyBuilder.setRealm(Dsl.basicAuthRealm(proxy.user(), proxy.password()));
            }

            builder.setProxyServer(proxyBuilder.build());
        }

        return builder.build();
    }

    default org.asynchttpclient.AsyncHttpClient nettyAsyncHttpClient(org.asynchttpclient.AsyncHttpClientConfig config) {
        return Dsl.asyncHttpClient(config);
    }

    default AsyncHttpClient asyncHttpClient(org.asynchttpclient.AsyncHttpClient client) {
        return new AsyncHttpClient(client);
    }
}
