package ru.tinkoff.kora.http.common.body;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.BlockingStreamingHttpBodyOutput.IOConsumer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

/**
 * <b>Русский</b>: Описывает тело HTTP ответа
 * <hr>
 * <b>English</b>: Describes HTTP response body
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpBody.of("application/octet-stream", new byte{ 0x0 })
 * }
 * </pre>
 */
public interface HttpBodyOutput extends HttpBody, Flow.Publisher<ByteBuffer> {

    static HttpBodyOutput of(String contentType, Flow.Publisher<? extends ByteBuffer> content) {
        return new StreamingHttpBodyOutput(contentType, -1, content);
    }

    static HttpBodyOutput of(String contentType, long length, Flow.Publisher<? extends ByteBuffer> content) {
        return new StreamingHttpBodyOutput(contentType, length, content);
    }

    static HttpBodyOutput of(String contentType, InputStream inputStream) {
        return new BlockingHttpBodyOutput(contentType, -1, HttpRequest.BodyPublishers.ofInputStream(() -> inputStream));
    }

    static HttpBodyOutput of(String contentType, long length, InputStream inputStream) {
        return new BlockingHttpBodyOutput(contentType, length, HttpRequest.BodyPublishers.ofInputStream(() -> inputStream));
    }

    static HttpBodyOutput ofBlockingStream(IOConsumer<OutputStream> ioConsumer) {
        return new BlockingStreamingHttpBodyOutput(null, -1, ioConsumer);
    }

    static HttpBodyOutput ofBlockingStream(String contentType, IOConsumer<OutputStream> ioConsumer) {
        return new BlockingStreamingHttpBodyOutput(contentType, -1, ioConsumer);
    }

    static HttpBodyOutput ofBlockingStream(String contentType, IOConsumer<OutputStream> ioConsumer, Closeable closeable) {
        return new BlockingStreamingHttpBodyOutput(contentType, -1, ioConsumer, closeable);
    }

    static HttpBodyOutput ofBlockingStream(String contentType, long length, IOConsumer<OutputStream> ioConsumer) {
        return new BlockingStreamingHttpBodyOutput(contentType, length, ioConsumer);
    }

    static HttpBodyOutput ofBlockingStream(String contentType, long length, IOConsumer<OutputStream> ioConsumer, Closeable closeable) {
        return new BlockingStreamingHttpBodyOutput(contentType, length, ioConsumer, closeable);
    }

    static HttpBodyOutput octetStream(Flow.Publisher<? extends ByteBuffer> content) {
        return new StreamingHttpBodyOutput("application/octet-stream", -1, content);
    }

    static HttpBodyOutput octetStream(InputStream inputStream) {
        return new BlockingHttpBodyOutput("application/octet-stream", -1, HttpRequest.BodyPublishers.ofInputStream(() -> inputStream));
    }

    static HttpBodyOutput octetStream(long length, Flow.Publisher<? extends ByteBuffer> content) {
        return new StreamingHttpBodyOutput("application/octet-stream", length, content);
    }

    static HttpBodyOutput ofBlockingOctetStream(IOConsumer<OutputStream> ioConsumer) {
        return new BlockingStreamingHttpBodyOutput("application/octet-stream", -1, ioConsumer);
    }

    static HttpBodyOutput ofBlockingOctetStream(IOConsumer<OutputStream> ioConsumer, Closeable closeable) {
        return new BlockingStreamingHttpBodyOutput("application/octet-stream", -1, ioConsumer, closeable);
    }

    static HttpBodyOutput ofBlockingOctetStream(long length, IOConsumer<OutputStream> ioConsumer) {
        return new BlockingStreamingHttpBodyOutput("application/octet-stream", length, ioConsumer);
    }

    static HttpBodyOutput ofBlockingOctetStream(long length, IOConsumer<OutputStream> ioConsumer, Closeable closeable) {
        return new BlockingStreamingHttpBodyOutput("application/octet-stream", length, ioConsumer, closeable);
    }

    long contentLength();

    @Nullable
    String contentType();

    @Override
    void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber);

    default void write(OutputStream os) throws IOException {
        var f = new CompletableFuture<Void>();
        this.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription s;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
                this.s = s;
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {
                if (byteBuffer.hasArray()) {
                    try {
                        os.write(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.remaining());
                    } catch (IOException e) {
                        this.s.cancel();
                        f.completeExceptionally(e);
                    }
                } else {
                    var arr = new byte[byteBuffer.remaining()];
                    byteBuffer.get(arr);
                    try {
                        os.write(arr);
                    } catch (IOException e) {
                        this.s.cancel();
                        f.completeExceptionally(e);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                try {
                    os.flush();
                    f.complete(null);
                } catch (IOException e) {
                    f.completeExceptionally(e);
                }
            }
        });
        try {
            f.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException(e.getCause());
        }
    }
}
