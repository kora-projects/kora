package io.koraframework.http.client.jdk;

import io.koraframework.http.client.common.*;
import io.koraframework.http.client.common.exception.*;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBodyOutput;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class JdkHttpClient implements HttpClient {
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
        var httpClientRequest = HttpRequest.newBuilder()
            .uri(request.uri());
        var timeout = request.requestTimeout() != null ? request.requestTimeout() : this.readTimeout;
        if (timeout != null) {
            httpClientRequest.timeout(timeout);
        }
        for (var header : request.headers()) {
            if (isRestrictedHeader(header.getKey())) {
                continue;
            }
            if (header.getKey().equalsIgnoreCase("content-type") && request.body().contentType() != null) {
                continue;
            }
            for (var value : header.getValue()) {
                httpClientRequest.header(header.getKey(), value);
            }
        }
        try (var body = request.body()) {
            if (body.contentType() != null) {
                httpClientRequest.header("content-type", body.contentType());
            }
            var bodyPublisher = this.toBodyPublisher(body);
            httpClientRequest.method(request.method(), bodyPublisher);
            try {
                var rs = this.httpClient.send(httpClientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
                return new JdkHttpClientResponse(rs);
            } catch (ProtocolException | java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
                throw new HttpClientConnectionException(e);
            } catch (java.net.http.HttpTimeoutException e) {
                throw new HttpClientTimeoutException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpClientUnknownException(e);
            } catch (IOException e) {
                if (e.getCause() instanceof HttpClientException h) {
                    throw h;
                }
                if (bodyPublisher instanceof SubscriptionTrackingBodyPublisher p && p.subscribed.get()) {
                    throw e;
                }
                try {
                    var rs = this.httpClient.send(httpClientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
                    return new JdkHttpClientResponse(rs);
                } catch (ProtocolException | java.net.http.HttpConnectTimeoutException e1) {
                    throw new HttpClientConnectionException(e1);
                } catch (java.net.http.HttpTimeoutException e1) {
                    throw new HttpClientTimeoutException(e1);
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new HttpClientUnknownException(ex);
                }
            }
        } catch (IOException e) {
            throw new HttpClientUnknownException(e);
        }
    }

    static boolean isRestrictedHeader(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "connection", "content-length", "expect", "host", "upgrade" -> true;
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
                return new SubscriptionTrackingBodyPublisher(HttpRequest.BodyPublishers.ofByteArray(full.array(), full.arrayOffset(), full.remaining()), true);
            } else {
                return new SubscriptionTrackingBodyPublisher(new JdkByteBufferBodyPublisher(full), true);
            }
        }

        return new SubscriptionTrackingBodyPublisher(new RequestBodyPublisher(body), !body.isOneShot());
    }

    /**
     * Remembers whether the JDK client started sending the body, so a request whose body may already be sent is not retried,
     * and fails a repeated subscription (e.g. a 307/308 redirect) for a body that can be written only once ({@link HttpBodyOutput#isOneShot()}).
     */
    private static final class SubscriptionTrackingBodyPublisher implements HttpRequest.BodyPublisher {
        private final HttpRequest.BodyPublisher delegate;
        private final boolean replayable;
        private final AtomicBoolean subscribed = new AtomicBoolean(false);

        private SubscriptionTrackingBodyPublisher(HttpRequest.BodyPublisher delegate, boolean replayable) {
            this.delegate = delegate;
            this.replayable = replayable;
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (!subscribed.compareAndSet(false, true) && !replayable) {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {}

                    @Override
                    public void cancel() {}
                });
                subscriber.onError(new HttpClientEncoderException(new IllegalStateException("Streaming request body can't be sent twice")));
                return;
            }
            delegate.subscribe(subscriber);
        }
    }

    private static class RequestBodyPublisher implements HttpRequest.BodyPublisher {
        private final HttpBodyOutput httpBodyOutput;

        private RequestBodyPublisher(HttpBodyOutput httpBodyOutput) {
            this.httpBodyOutput = httpBodyOutput;
        }

        @Override
        public long contentLength() {
            return httpBodyOutput.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new StreamSubscription(httpBodyOutput, subscriber));
        }
    }

    /**
     * Runs {@link HttpBodyOutput#write} on its own virtual thread: every chunk is copied, because writers reuse their buffers,
     * and the writer blocks until the JDK client requests more data.
     */
    private static class StreamSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final HttpBodyOutput body;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition demandChanged = lock.newCondition();
        private long demand = 0;
        private volatile boolean cancelled = false;

        private StreamSubscription(HttpBodyOutput body, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.body = body;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            lock.lock();
            try {
                if (cancelled) {
                    return;
                }
                demand = demand + n < 0 ? Long.MAX_VALUE : demand + n;
                demandChanged.signal();
            } finally {
                lock.unlock();
            }
            if (started.compareAndSet(false, true)) {
                Thread.ofVirtual().name("kora-jdk-http-client-body-writer").start(this::write);
            }
        }

        @Override
        public void cancel() {
            lock.lock();
            try {
                cancelled = true;
                demandChanged.signal();
            } finally {
                lock.unlock();
            }
        }

        private void write() {
            var out = new OutputStream() {
                @Override
                public void write(int b) {
                    throw new IllegalStateException();
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    awaitDemand();
                    subscriber.onNext(ByteBuffer.wrap(Arrays.copyOfRange(b, off, off + len)));
                }
            };
            try (var stream = new BufferedOutputStream(out); this.body) {
                body.write(stream);
            } catch (Exception e) {
                if (!cancelled) {
                    subscriber.onError(new HttpClientEncoderException(e));
                }
                return;
            }
            if (!cancelled) {
                subscriber.onComplete();
            }
        }

        private void awaitDemand() throws IOException {
            lock.lock();
            try {
                while (demand == 0 && !cancelled) {
                    demandChanged.awaitUninterruptibly();
                }
                if (cancelled) {
                    throw new IOException("Request body subscription was cancelled");
                }
                demand--;
            } finally {
                lock.unlock();
            }
        }
    }
}
