package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetry.DefaultHttpClientTelemetryContextImpl;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultHttpClientTelemetryResponseBodyWrapper extends AtomicBoolean implements HttpBodyInput {
    private final HttpClientResponse response;
    private final DefaultHttpClientTelemetryContextImpl telemetryContext;
    private volatile InputStream is;

    public DefaultHttpClientTelemetryResponseBodyWrapper(HttpClientResponse response, DefaultHttpClientTelemetryContextImpl telemetryContext) {
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
        try (var _ = this.is; var _ = this.response) {} finally {
            if (this.compareAndSet(false, true)) {
                telemetryContext.onClose(response.code(), response.headers(), null, null);
            }
        }
    }

    private static final class WrappedInputStream extends InputStream {
        private final InputStream is;
        private final DefaultHttpClientTelemetryContextImpl telemetryContext;
        private final HttpClientResponse response;
        private boolean telemetryClosed;

        public WrappedInputStream(DefaultHttpClientTelemetryContextImpl telemetryContext, HttpClientResponse response, InputStream inputStream) {
            this.is = inputStream;
            this.telemetryContext = telemetryContext;
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            try {
                var b = is.read();
                if (b < 0) {
                    telemetryClosed = true;
                    telemetryContext.onClose(response.code(), response.headers(), null, null);
                }
                return b;
            } catch (IOException e) {
                try {
                    telemetryClosed = true;
                    telemetryContext.onClose(e);
                } catch (Throwable t) {
                    e.addSuppressed(t);
                }
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                var read = is.read(b, off, len);
                if (read < 0) {
                    telemetryClosed = true;
                    telemetryContext.onClose(response.code(), response.headers(), null, null);
                }
                return read;
            } catch (IOException e) {
                try {
                    telemetryClosed = true;
                    telemetryContext.onClose(e);
                } catch (Throwable t) {
                    e.addSuppressed(t);
                }
                throw e;
            }
        }

        @Override
        public void close() {
            if (!telemetryClosed) {
                telemetryClosed = true;
                telemetryContext.onClose(response.code(), response.headers(), null, null);
            }
        }
    }
}
