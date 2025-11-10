package ru.tinkoff.kora.http.client.common.telemetry.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultHttpClientTelemetryCollectingResponseBodyWrapper extends AtomicBoolean implements HttpBodyInput {
    private final DefaultHttpClientLogger logger;
    private final HttpClientRequest rq;
    private final HttpClientResponse rs;
    private final long processingTime;
    private final Charset charset;

    private volatile InputStream is;

    public DefaultHttpClientTelemetryCollectingResponseBodyWrapper(DefaultHttpClientLogger logger, HttpClientRequest rq, HttpClientResponse rs, long processingTime, Charset charset) {
        this.logger = logger;
        this.rq = rq;
        this.rs = rs;
        this.processingTime = processingTime;
        this.charset = charset;
    }

    @Override
    public long contentLength() {
        return rs.body().contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        return rs.body().contentType();
    }

    @Override
    public InputStream asInputStream() {
        var is = this.is;
        if (is != null) {
            return is;
        }
        if (this.compareAndSet(false, true)) {
            is = new WrappedInputStream(logger, rs, rs.body().asInputStream());
            return this.is = is;
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public void close() throws IOException {
        try (var body = this.rs.body(); var _ = this.is) {
            if (this.compareAndSet(false, true)) {
                // input stream was never requested, so we should just collect body here
                try {
                    var buf = body.asInputStream().readAllBytes();
                    logger.logResponse(rq, rs, processingTime, new String(buf, charset));
                } catch (IOException e) {
                    logger.logError(rq, processingTime, e);
                    throw e;
                }
            }
        }
    }

    private class WrappedInputStream extends InputStream {
        private final InputStream is;
        private final DefaultHttpClientLogger logger;
        private final HttpClientResponse response;
        private final List<ByteBuffer> body = new ArrayList<>();
        private boolean closed = false;

        public WrappedInputStream(DefaultHttpClientLogger logger, HttpClientResponse response, InputStream inputStream) {
            this.is = inputStream;
            this.logger = logger;
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
                    logger.logResponse(rq, rs, processingTime, bodyToString(body));
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
                    logger.logError(rq, processingTime, e);
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
                        logger.logResponse(rq, rs, processingTime, bodyToString(body));
                    }
                }
            }
        }

        private String bodyToString(List<ByteBuffer> body) {

            // TODO
            return null;
        }
    }
}
