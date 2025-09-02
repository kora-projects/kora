package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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
        try (var body = this.response.body(); var _ = this.is) {
            if (this.compareAndSet(false, true)) {
                // input stream was never requested, so we should just collect body here
                try {
                    var buf = body.asInputStream().readAllBytes();
                    telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), List.of(ByteBuffer.wrap(buf)));
                } catch (IOException e) {
                    telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), null);
                    throw e;
                }
                new WrappedInputStream(telemetryContext, response, response.body().asInputStream()).readAllBytes();
            }
        }
    }

    private static class WrappedInputStream extends InputStream {
        private final InputStream is;
        private final DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl telemetryContext;
        private final HttpClientResponse response;
        private final List<ByteBuffer> body = new ArrayList<>();
        private boolean closed = false;

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
                    closed = true;
                    telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), body);
                }
                if (read > 0) {
                    var copy = ByteBuffer.allocate(read);
                    copy.put(b, off, read);
                    copy.rewind();
                    body.add(copy);
                }
                return read;
            } catch (IOException e) {
                try {
                    closed = true;
                    telemetryContext.onClose(e);
                } catch (Throwable t) {
                    e.addSuppressed(t);
                }
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try (this.is) {
                if (!closed) {
                    closed = true;
                    try {
                        while (true) {
                            var buf = new byte[1024];
                            var read = this.is.read(buf);
                            if (read < 0) {
                                break;
                            }
                            if (read > 0) {
                                this.body.add(ByteBuffer.wrap(buf, 0, read));
                            }
                        }
                    } finally {
                        telemetryContext.onClose(response.code(), response.headers(), response.body().contentType(), body);
                    }
                }
            }
        }
    }
}
