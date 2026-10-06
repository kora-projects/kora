package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.exception.HttpClientConnectionException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.exception.HttpClientUnknownException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.EmptyHttpBody;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.concurrent.Cancellable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApacheHttpClient implements HttpClient {

    private static final ScheduledThreadPoolExecutor DEADLINES = deadlineScheduler();

    private final org.apache.hc.client5.http.classic.HttpClient httpClient;

    public ApacheHttpClient(org.apache.hc.client5.http.classic.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpClientResponse execute(HttpClientRequest request) {
        ScheduledFuture<?> deadline = null;
        var deadlineReached = new AtomicBoolean();
        try (var _ = request.body()) {
            var apacheRequest = convertToApacheRequest(request);
            if (request.requestTimeout() != null && apacheRequest instanceof Cancellable cancellable) {
                // responseTimeout is a per-read socket timeout, so requestTimeout also needs a deadline for the whole call
                deadline = DEADLINES.schedule(() -> {
                    deadlineReached.set(true);
                    cancellable.cancel();
                }, request.requestTimeout().toNanos(), TimeUnit.NANOSECONDS);
            }
            var apacheResponse = httpClient.executeOpen(null, apacheRequest, null);
            var response = new ApacheHttpClientResponse(apacheResponse, deadline);
            deadline = null; // the response cancels it on close, so the deadline also covers reading the body
            return response;
        } catch (HttpClientException e) {
            throw e;
        } catch (Throwable t) {
            if (deadlineReached.get()) {
                var timeout = new InterruptedIOException("Request timeout of " + request.requestTimeout().toMillis() + "ms exceeded");
                timeout.initCause(t);
                throw new HttpClientTimeoutException(timeout);
            }
            if (t instanceof ConnectTimeoutException e) {
                throw new HttpClientConnectionException(e);
            } else if (t instanceof SocketTimeoutException e) {
                throw new HttpClientTimeoutException(e);
            } else if (t instanceof IOException e) {
                throw new HttpClientConnectionException(e);
            } else {
                throw new HttpClientUnknownException(t);
            }
        } finally {
            if (deadline != null) {
                deadline.cancel(false);
            }
        }
    }

    private static ScheduledThreadPoolExecutor deadlineScheduler() {
        var executor = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("kora-apache-http-client-deadline").factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    ClassicHttpRequest convertToApacheRequest(HttpClientRequest request) {
        var apacheRequest = new HttpUriRequestBase(request.method(), request.uri());
        if (request.requestTimeout() != null) {
            // a request config replaces the client default one completely, so start from the default
            var defaultConfig = httpClient instanceof Configurable configurable ? configurable.getConfig() : null;
            var config = defaultConfig != null ? RequestConfig.copy(defaultConfig) : RequestConfig.custom();
            apacheRequest.setConfig(config
                .setResponseTimeout(request.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build());
        }

        var bodyContentType = request.body() != null && !(request.body() instanceof EmptyHttpBody)
            ? request.body().contentType()
            : null;
        for (var header : request.headers()) {
            // The body content type wins over the header, as in the other transports
            if (bodyContentType != null && header.getKey().equalsIgnoreCase("content-type")) {
                continue;
            }
            // Apache derives entity framing from HttpEntity. Copying either header makes
            // RequestContent reject otherwise valid requests (for example AWS SDK uploads).
            if (header.getKey().equalsIgnoreCase("content-length")
                || header.getKey().equalsIgnoreCase("transfer-encoding")) {
                continue;
            }
            var values = header.getValue();
            for (var value : values) {
                apacheRequest.addHeader(header.getKey(), value);
            }
        }

        if (request.body() != null && !(request.body() instanceof EmptyHttpBody)) {
            apacheRequest.setEntity(new ApacheHttpRequestBody(request));
        }

        return apacheRequest;
    }
}
