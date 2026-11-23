package io.koraframework.http.client.jdk;

import io.koraframework.http.client.common.*;
import io.koraframework.http.client.common.exception.*;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBodyOutput;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.net.ProtocolException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JdkHttpClient implements HttpClient {
    private static final ScheduledThreadPoolExecutor BODY_DEADLINE_TIMER = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("kora-jdk-http-client-deadline").factory());

    static {
        BODY_DEADLINE_TIMER.setRemoveOnCancelPolicy(true);
    }

    private final java.net.http.HttpClient httpClient;
    @Nullable
    private final Duration readTimeout;

    public JdkHttpClient(java.net.http.HttpClient client) {
        this(client, null);
    }

    public JdkHttpClient(java.net.http.HttpClient client, @Nullable Duration readTimeout) {
        this.httpClient = client;
        this.readTimeout = readTimeout != null && readTimeout.isPositive() ? readTimeout : null;
    }

    @Override
    public HttpClientResponse execute(HttpClientRequest request) {
        var requestTimeout = request.requestTimeout();
        var deadline = requestTimeout != null ? System.nanoTime() + requestTimeout.toNanos() : 0L;
        try (var body = request.body()) {
            var httpClientRequest = HttpRequest.newBuilder()
                .uri(request.uri());
            var timeout = requestTimeout != null ? requestTimeout : this.readTimeout;
            if (timeout != null) {
                httpClientRequest.timeout(timeout);
            }
            for (var header : request.headers()) {
                if (isRestrictedHeader(header.getKey())) {
                    continue;
                }
                if (header.getKey().equalsIgnoreCase("content-type") && body.contentType() != null) {
                    continue;
                }
                for (var value : header.getValue()) {
                    httpClientRequest.header(header.getKey(), value);
                }
            }
            HttpResponse.BodyHandler<InputStream> bodyHandler = requestTimeout == null
                ? HttpResponse.BodyHandlers.ofInputStream()
                : info -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream(), is -> new DeadlineInputStream(is, deadline));
            if (body.contentType() != null) {
                httpClientRequest.header("content-type", body.contentType());
            }
            var bodyPublisher = this.toBodyPublisher(body);
            httpClientRequest.method(request.method(), bodyPublisher);
            try {
                var rs = this.httpClient.send(httpClientRequest.build(), bodyHandler);
                return new JdkHttpClientResponse(rs);
            } catch (ProtocolException | java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
                throw new HttpClientConnectionException(e);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new HttpClientTimeoutException(e);
            } catch (InterruptedException e) {
                throw new HttpClientUnknownException(e);
            } catch (IOException e) {
                if (e.getCause() instanceof HttpClientException h) {
                    throw h;
                }
                if (bodyPublisher instanceof RequestBodyPublisher r && r.subscribed) {
                    throw new HttpClientConnectionException(e);
                }
                try {
                    var rs = this.httpClient.send(httpClientRequest.build(), bodyHandler);
                    return new JdkHttpClientResponse(rs);
                } catch (ProtocolException | java.net.http.HttpConnectTimeoutException e1) {
                    throw new HttpClientConnectionException(e1);
                } catch (java.net.http.HttpTimeoutException e1) {
                    throw new HttpClientTimeoutException(e1);
                } catch (IOException e1) {
                    throw new HttpClientConnectionException(e1);
                } catch (Exception ex) {
                    throw new HttpClientUnknownException(ex);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new HttpClientUnknownException(e);
        }
    }

    static boolean isRestrictedHeader(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "connection", "content-length", "expect", "host", "upgrade", "transfer-encoding" -> true;
            default -> false;
        };
    }

    private HttpRequest.BodyPublisher toBodyPublisher(HttpBodyOutput body) throws IOException {
        if (body.contentLength() == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            if (full.remaining() == 0) {
                return HttpRequest.BodyPublishers.noBody();
            }
            if (full.hasArray()) {
                return HttpRequest.BodyPublishers.ofByteArray(full.array(), full.arrayOffset(), full.remaining());
            } else {
                return new JdkByteBufferBodyPublisher(full);
            }
        }

        return new RequestBodyPublisher(body);
    }

    /**
     * {@link HttpRequest#timeout} only bounds waiting for the response headers, this bounds reading the body with the rest of the request timeout
     */
    private static final class DeadlineInputStream extends FilterInputStream {
        private final ScheduledFuture<?> timer;
        private volatile boolean expired;

        private DeadlineInputStream(InputStream in, long deadline) {
            super(in);
            this.timer = BODY_DEADLINE_TIMER.schedule(this::expire, deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        private void expire() {
            this.expired = true;
            try {
                this.in.close();
            } catch (IOException ignored) {
            }
        }

        @Override
        public int read() throws IOException {
            try {
                var r = super.read();
                if (r < 0) {
                    this.timer.cancel(false);
                }
                return r;
            } catch (IOException e) {
                throw this.mapException(e);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                var r = super.read(b, off, len);
                if (r < 0) {
                    this.timer.cancel(false);
                }
                return r;
            } catch (IOException e) {
                throw this.mapException(e);
            }
        }

        @Override
        public long skip(long n) throws IOException {
            try {
                return super.skip(n);
            } catch (IOException e) {
                throw this.mapException(e);
            }
        }

        private IOException mapException(IOException e) {
            if (this.expired) {
                var timeout = new HttpTimeoutException("Request timed out while reading response body");
                timeout.initCause(e);
                throw new HttpClientTimeoutException(timeout);
            }
            return e;
        }

        @Override
        public void close() throws IOException {
            this.timer.cancel(false);
            super.close();
        }
    }

    private static class RequestBodyPublisher implements HttpRequest.BodyPublisher {
        private final HttpBodyOutput httpBodyOutput;
        private volatile boolean subscribed = false;

        private RequestBodyPublisher(HttpBodyOutput httpBodyOutput) {
            this.httpBodyOutput = httpBodyOutput;
        }

        @Override
        public long contentLength() {
            return httpBodyOutput.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscribed = true;
            subscriber.onSubscribe(new StreamSubscription(httpBodyOutput, subscriber));
        }
    }

    private static class StreamSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private boolean wip = false;
        private boolean completed = false;
        private final HttpBodyOutput body;

        private StreamSubscription(HttpBodyOutput body, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.body = body;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (wip || completed) {
                return;
            }
            wip = true;
            var out = new OutputStream() {
                @Override
                public void write(int b) {
                    throw new IllegalStateException();
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    subscriber.onNext(ByteBuffer.wrap(b, off, len));
                }
            };
            try (var stream = new BufferedOutputStream(out); this.body) {
                body.write(stream);
            } catch (Exception e) {
                completed = true;
                subscriber.onError(new HttpClientEncoderException(e));
                return;
            } finally {
                wip = false;
            }
            completed = true;
            subscriber.onComplete();
        }

        @Override
        public void cancel() {

        }
    }
}
