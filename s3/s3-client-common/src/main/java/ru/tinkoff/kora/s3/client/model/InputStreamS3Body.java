package ru.tinkoff.kora.s3.client.model;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public record InputStreamS3Body(InputStream inputStream,
                                long size,
                                String type,
                                String encoding) implements S3Body {

    public InputStreamS3Body(InputStream inputStream, long size, String type, String encoding) {
        this.inputStream = inputStream;
        this.size = size;
        this.type = (type == null || type.isBlank()) ? "application/octet-stream" : type;
        this.encoding = encoding;
    }

    @Override
    public InputStream asInputStream() {
        return inputStream;
    }

    @Override
    public Flow.Publisher<ByteBuffer> asPublisher() {
        return HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);
    }
}
