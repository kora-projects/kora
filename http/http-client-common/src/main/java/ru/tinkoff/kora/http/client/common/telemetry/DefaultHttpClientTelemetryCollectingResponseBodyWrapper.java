package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultHttpClientTelemetryCollectingResponseBodyWrapper extends AtomicBoolean implements HttpBodyInput {
    private final HttpClientResponse response;
    private final DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext;
    private volatile InputStream is;

    public DefaultHttpClientTelemetryCollectingResponseBodyWrapper(HttpClientResponse response, DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext) {
        this.response = response;
        this.telemetryContext = telemetryContext;
    }

    @Override
    public long contentLength() {
        return response.body().contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        return response.body().contentType();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (this.compareAndSet(false, true)) {
            var s = new ByteBufferSubscriber(response, subscriber);
            response.body().subscribe(s);
            subscriber.onSubscribe(s);
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public InputStream asInputStream() {
        var is = this.is;
        if (is != null) {
            return is;
        }
        if (this.compareAndSet(false, true)) {
            is = new WrappedInputStream(telemetryContext, response, response.body().asInputStream());
            return this.is = is;
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public void close() throws IOException {
        if (this.is != null) {
            try {
                this.is.close();
            } finally {
                this.response.close();
            }
        } else {
            if (compareAndSet(false, true)) {
                this.response.body().subscribe(new DrainSubscriber(this.response, this.telemetryContext));
            }
        }
    }

    private class ByteBufferSubscriber implements Flow.Subscriber<ByteBuffer>, Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final HttpClientResponse response;
        private final List<ByteBuffer> body = Collections.synchronizedList(new ArrayList<>());
        private Flow.Subscription subscription;

        public ByteBufferSubscriber(HttpClientResponse response, Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.response = response;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(ByteBuffer item) {
            var copy = ByteBuffer.allocate(item.remaining());
            copy.put(item.slice());
            copy.rewind();
            body.add(copy);
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                telemetryContext.onClose(throwable);
            } finally {
                subscriber.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            try {
                telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), body);
            } finally {
                subscriber.onComplete();
            }
        }

        @Override
        public void request(long n) {
            subscription.request(n);
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }
    }

    private static class DrainSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final HttpClientResponse response;
        private final DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext;
        private final List<ByteBuffer> body = Collections.synchronizedList(new ArrayList<>());

        public DrainSubscriber(HttpClientResponse response, DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext) {
            this.response = response;
            this.telemetryContext = telemetryContext;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            var copy = ByteBuffer.allocate(item.remaining());
            copy.put(item.slice());
            copy.rewind();
            body.add(copy);
        }

        @Override
        public void onError(Throwable throwable) {
            telemetryContext.onClose(throwable);
        }

        @Override
        public void onComplete() {
            telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), body);
        }
    }

    private static class WrappedInputStream extends InputStream {
        private final InputStream is;
        private final DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext;
        private final HttpClientResponse response;
        private final List<ByteBuffer> body = new ArrayList<>();

        public WrappedInputStream(DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext, HttpClientResponse response, InputStream inputStream) {
            this.is = inputStream;
            this.telemetryContext = telemetryContext;
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            var b = new byte[1];
            var read = this.read(b);
            if (read < 0) {
                return read;
            }
            return b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                var read = is.read(b, off, len);
                if (read < 0) {
                    telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), body);
                }
                if (read > 0) {
                    var buf = ByteBuffer.allocate(read);
                    buf.put(b, off, read);
                    body.add(buf);
                }
                return read;
            } catch (IOException e) {
                try {
                    telemetryContext.onClose(e);
                } catch (Throwable t) {
                    e.addSuppressed(t);
                }
                throw e;
            }
        }
    }
}
