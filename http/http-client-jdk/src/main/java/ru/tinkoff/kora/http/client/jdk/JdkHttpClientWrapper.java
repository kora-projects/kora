package ru.tinkoff.kora.http.client.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class JdkHttpClientWrapper implements Lifecycle, Wrapped<HttpClient> {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpClientWrapper.class);

    private final JdkHttpClientConfig config;
    private final HttpClientConfig baseConfig;
    private volatile HttpClient client;
    private volatile ExecutorService executor;

    public JdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        this.config = config;
        this.baseConfig = baseConfig;
    }

    @Override
    public void init() {
        logger.debug("JdkHttpClient starting...");
        var started = System.nanoTime();

        var executorThreads = this.config.threads();
        this.executor = Executors.newFixedThreadPool(executorThreads);
        var builder = HttpClient.newBuilder()
            .version(this.config.httpVersion())
            .executor(this.executor)
            .connectTimeout(this.baseConfig.connectTimeout())
            .followRedirects(this.config.followRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        var proxyConfig = this.baseConfig.proxy();
        if (this.baseConfig.useEnvProxy()) {
            proxyConfig = HttpClientConfig.HttpClientProxyConfig.fromEnv();
        }
        if (proxyConfig != null) {
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
        logger.info("JdkHttpClient started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("JdkHttpClient stopping...");
        var started = System.nanoTime();

        this.client = null;
        var e = this.executor;
        this.executor = null;
        e.shutdown();

        logger.info("JdkHttpClient stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public HttpClient value() {
        return this.client;
    }
}
