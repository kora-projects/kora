package io.koraframework.http.client.apache;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.util.Configurer;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.http.client.common.HttpClientConfig;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.util.TimeValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ApacheHttpClientWrapper implements Lifecycle, Wrapped<org.apache.hc.client5.http.classic.HttpClient> {

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClientWrapper.class);

    private final HttpClientConfig baseConfig;
    private final ApacheHttpClientConfig apacheConfig;
    @Nullable
    private final Configurer<RequestConfig.Builder> requestConfigurer;
    @Nullable
    private final Configurer<HttpClientBuilder> clientConfigurer;

    private volatile CloseableHttpClient httpClient;

    public ApacheHttpClientWrapper(HttpClientConfig baseConfig,
                                   ApacheHttpClientConfig apacheConfig,
                                   @Nullable Configurer<RequestConfig.Builder> requestConfigurer,
                                   @Nullable Configurer<HttpClientBuilder> clientConfigurer) {
        this.baseConfig = baseConfig;
        this.apacheConfig = apacheConfig;
        this.requestConfigurer = requestConfigurer;
        this.clientConfigurer = clientConfigurer;
    }

    private CloseableHttpClient createApacheHttpClient() {
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

        if (apacheConfig.connectTimeout() != null) {
            requestConfigBuilder.setConnectTimeout(apacheConfig.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (apacheConfig.readTimeout() != null) {
            requestConfigBuilder.setResponseTimeout(apacheConfig.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        requestConfigBuilder = requestConfigBuilder
            .setAuthenticationEnabled(false)
            .setCircularRedirectsAllowed(false)
            .setContentCompressionEnabled(false)
            .setHardCancellationEnabled(true)
            .setExpectContinueEnabled(false)
            .setProtocolUpgradeEnabled(true)
            .setRedirectsEnabled(apacheConfig.followRedirects())
            .setMaxRedirects(apacheConfig.maxRedirects());

        // Connection manager for pooling
        var connectionPoolBuilder = PoolingHttpClientConnectionManagerBuilder.create()
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.OFFLOCK)
            .setMaxConnTotal(apacheConfig.maxConnections())
            .setMaxConnPerRoute(apacheConfig.maxConnections())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(apacheConfig.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .setIdleTimeout(30, TimeUnit.SECONDS)
                .setValidateAfterInactivity(30, TimeUnit.SECONDS)
                .build());

        // Build the client
        if (requestConfigurer != null) {
            requestConfigBuilder = requestConfigurer.configure(requestConfigBuilder);
        }

        var clientBuilder = HttpClients.custom()
            .setDefaultRequestConfig(requestConfigBuilder.build())
            .setConnectionManager(connectionPoolBuilder.build())
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .disableDefaultUserAgent()
            .disableAuthCaching()
            .disableConnectionState()
            .disableAutomaticRetries();

        var proxyConfig = this.baseConfig.proxy();
        if (this.baseConfig.useEnvProxy()) {
            proxyConfig = HttpClientConfig.HttpClientProxyConfig.fromEnv();
        }
        if (proxyConfig != null) {
            clientBuilder.setProxySelector(new JdkProxySelector(proxyConfig));
            var proxyUser = proxyConfig.user();
            var proxyPassword = proxyConfig.password();
            if (proxyUser != null && proxyPassword != null) {
                var credProvider = new BasicCredentialsProvider();
                var proxyScope = new AuthScope(proxyConfig.host(), proxyConfig.port());
                var proxyCredentials = new UsernamePasswordCredentials(proxyUser, proxyPassword.toCharArray());
                credProvider.setCredentials(proxyScope, proxyCredentials);
                clientBuilder.setDefaultCredentialsProvider(credProvider);
            }
        }

        if (clientConfigurer != null) {
            clientBuilder = clientConfigurer.configure(clientBuilder);
        }

        return clientBuilder.build();
    }

    @Override
    public void init() {
        logger.debug("ApacheHttpClient starting...");
        var started = System.nanoTime();

        this.httpClient = createApacheHttpClient();
        logger.info("ApacheHttpClient started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("ApacheHttpClient stopping...");
        var started = System.nanoTime();

        if (this.httpClient != null) {
            this.httpClient.close(CloseMode.GRACEFUL);
        }
        this.httpClient = null;

        logger.info("ApacheHttpClient stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public org.apache.hc.client5.http.classic.HttpClient value() {
        return null;
    }
}
