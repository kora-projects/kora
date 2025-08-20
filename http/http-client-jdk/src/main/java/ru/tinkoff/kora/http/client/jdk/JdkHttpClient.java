package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.http.client.common.*;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public class JdkHttpClient implements HttpClient {
    private final java.net.http.HttpClient httpClient;

    public JdkHttpClient(java.net.http.HttpClient client) {
        this.httpClient = client;
    }

    @Override
    public HttpClientResponse execute(HttpClientRequest request) {
        var httpClientRequest = HttpRequest.newBuilder()
            .uri(request.uri());
        if (request.requestTimeout() != null) {
            httpClientRequest.timeout(request.requestTimeout());
        }
        for (var header : request.headers()) {
            if (header.getKey().equalsIgnoreCase("content-length")) {
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
            } catch (ProtocolException | java.net.http.HttpConnectTimeoutException e) {
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
                    throw new HttpClientUnknownException(ex);
                }
            }
        } catch (IOException e) {
            throw new HttpClientUnknownException(e);
        }
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
