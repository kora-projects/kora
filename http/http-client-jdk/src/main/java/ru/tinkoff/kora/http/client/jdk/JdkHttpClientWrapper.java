package ru.tinkoff.kora.http.client.jdk;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class JdkHttpClientWrapper implements Lifecycle, Wrapped<HttpClient> {
    private final JdkHttpClientConfig config;
    private final HttpClientConfig baseConfig;
    private volatile HttpClient client;
    private volatile ExecutorService executor;

    public JdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        this.config = config;
        this.baseConfig = baseConfig;
    }

    @Override
    public Mono<?> init() {
        return Mono.fromRunnable(() -> {
            var executorThreads = this.config.threads();
            if (executorThreads == null) {
                executorThreads = Runtime.getRuntime().availableProcessors() * 2;
            }
            this.executor = Executors.newFixedThreadPool(executorThreads);
            var builder = HttpClient.newBuilder()
                .executor(this.executor)
                .connectTimeout(Duration.ofMillis(this.baseConfig.connectTimeout()))
                .followRedirects(HttpClient.Redirect.NORMAL);
            var proxyConfig = this.baseConfig.proxy();
            if (this.baseConfig.useEnvProxy() != null && this.baseConfig.useEnvProxy()) {
                proxyConfig = HttpClientConfig.HttpClientProxyConfig.fromEnv();
            }
            if (proxyConfig != null && proxyConfig.host() != null && proxyConfig.port() != null) {
                builder.proxy(new JdkProxySelector(proxyConfig));
                var proxyUser = proxyConfig.user();
                var proxyPassword = proxyConfig.password();
                if (proxyUser != null && proxyPassword != null) {
                    var proxyPasswordCharArray = proxyPassword.toCharArray();
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(proxyUser, proxyPasswordCharArray);
                        }
                    });
                }
            }
            this.client = builder.build();
        });
    }

    @Override
    public Mono<?> release() {
        return Mono.fromRunnable(() -> {
            this.client = null;
            var e = this.executor;
            this.executor = null;
            e.shutdown();
        });
    }

    @Override
    public HttpClient value() {
        return this.client;
    }
}
