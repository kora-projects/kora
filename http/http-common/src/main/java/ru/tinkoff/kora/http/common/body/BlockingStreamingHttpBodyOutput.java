package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public class BlockingStreamingHttpBodyOutput implements HttpBodyOutput {

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(T value) throws IOException;
    }

    @Nullable
    private final String contentType;
    private final long contentLength;
    private final IOConsumer<OutputStream> outputStreamConsumer;
    private final Closeable closeable;

    public BlockingStreamingHttpBodyOutput(@Nullable String contentType,
                                           long contentLength,
                                           IOConsumer<OutputStream> outputStreamConsumer) {
        this(contentType, contentLength, outputStreamConsumer, () -> { });
    }

    public BlockingStreamingHttpBodyOutput(@Nullable String contentType,
                                           long contentLength,
                                           IOConsumer<OutputStream> outputStreamConsumer,
                                           Closeable closeable) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.outputStreamConsumer = outputStreamConsumer;
        this.closeable = closeable;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Nullable
    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        throw new IllegalStateException("Unexpected subscription to BlockingStreamingHttpBodyOutput");
    }

    @Override
    public void write(OutputStream os) throws IOException {
        outputStreamConsumer.accept(os);
    }

    @Override
    public void close() throws IOException {
        closeable.close();
    }
}
