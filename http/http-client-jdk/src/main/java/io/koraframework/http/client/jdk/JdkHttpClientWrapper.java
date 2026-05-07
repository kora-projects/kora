package io.koraframework.http.client.jdk;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.http.client.common.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;


public final class JdkHttpClientWrapper implements Lifecycle, Wrapped<HttpClient> {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpClientWrapper.class);

    private final JdkHttpClientConfig config;
    private final HttpClientConfig baseConfig;

    private volatile HttpClient client;

    public JdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        this.config = config;
        this.baseConfig = baseConfig;
    }

    @Override
    public void init() {
        logger.debug("JdkHttpClient starting...");
        var started = System.nanoTime();

        var executor = getVirtualExecutor();
        var builder = HttpClient.newBuilder()
            .version(this.config.httpVersion())
            .executor(executor)
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

        logger.info("JdkHttpClient stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public HttpClient value() {
        return this.client;
    }

    private static Executor getVirtualExecutor() {
        final ThreadFactory virtualFactory = Thread.ofVirtual().name("http-client-jdk-").factory();
        return runnable -> {
            Thread thread = virtualFactory.newThread(runnable);
            thread.start();
        };
    }
}
