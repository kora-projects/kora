package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBodyInput;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultHttpClientTelemetryCollectingResponseBodyWrapper extends AtomicBoolean implements HttpBodyInput {

    private final DefaultHttpClientLogger logger;
    private final HttpClientRequest request;
    private final HttpClientResponse response;
    private final long processingTookNanos;

    private volatile InputStream is;

    public DefaultHttpClientTelemetryCollectingResponseBodyWrapper(DefaultHttpClientLogger logger,
                                                                   HttpClientRequest request,
                                                                   HttpClientResponse response,
                                                                   long processingTookNanos) {
        this.logger = logger;
        this.request = request;
        this.response = response;
        this.processingTookNanos = processingTookNanos;
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
            is = new WrappedInputStream(logger, response.body().asInputStream());
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
                    if (logger.logResponseBody()) {
                        var buf = body.asInputStream().readAllBytes();
                        if (buf.length > 0) {
                            logger.logResponse(request, response, processingTookNanos, ByteBuffer.wrap(buf), body.contentType());
                        } else {
                            logger.logResponse(request, response, processingTookNanos, null, body.contentType());
                        }
                    } else {
                        logger.logResponse(request, response, processingTookNanos, null, body.contentType());
                    }
                } catch (IOException e) {
                    logger.logError(request, processingTookNanos, e);
                    throw e;
                }
            }
        }
    }

    private class WrappedInputStream extends InputStream {

        private static final byte[] EMPTY = new byte[]{};

        private final InputStream is;
        private final DefaultHttpClientLogger logger;
        private final List<ByteBuffer> body = new ArrayList<>();

        private boolean closed = false;

        public WrappedInputStream(DefaultHttpClientLogger logger, InputStream inputStream) {
            this.is = inputStream;
            this.logger = logger;
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
                    if (logger.logResponseBody()) {
                        var bodyArray = bodyToByteArray(body);
                        if (bodyArray.length > 0) {
                            logger.logResponse(request, response, processingTookNanos, ByteBuffer.wrap(bodyArray), contentType());
                        } else {
                            logger.logResponse(request, response, processingTookNanos, null, contentType());
                        }
                    } else {
                        logger.logResponse(request, response, processingTookNanos, null, contentType());
                    }
                } else if (read > 0) {
                    var copy = ByteBuffer.allocate(read);
                    copy.put(b, off, read);
                    copy.rewind();
                    body.add(copy);
                }
                return read;
            } catch (IOException e) {
                try {
                    closed = true;
                    logger.logError(request, processingTookNanos, e);
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
                        if (logger.logResponseBody()) {
                            var bodyArray = bodyToByteArray(body);
                            if (bodyArray.length > 0) {
                                logger.logResponse(request, response, processingTookNanos, ByteBuffer.wrap(bodyArray), contentType());
                            } else {
                                logger.logResponse(request, response, processingTookNanos, null, contentType());
                            }
                        } else {
                            logger.logResponse(request, response, processingTookNanos, null, contentType());
                        }
                    }
                }
            }
        }

        private byte[] bodyToByteArray(List<ByteBuffer> body) {
            int totalSize = 0;
            for (ByteBuffer b : body) {
                totalSize += b.remaining(); // remaining() = limit - position
            }

            if (totalSize == 0) {
                return EMPTY;
            }

            byte[] result = new byte[totalSize];
            int offset = 0;

            for (ByteBuffer b : body) {
                int length = b.remaining();
                if (length > 0) {
                    b.get(result, offset, length);
                    offset += length;
                }
            }

            return result;
        }
    }
}
