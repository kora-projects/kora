package ru.tinkoff.kora.s3.client.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public record ByteS3Body(byte[] bytes,
                         long size,
                         String type,
                         String encoding) implements S3Body {

    @Override
    public byte[] asBytes() {
        return bytes;
    }

    @Override
    public InputStream asInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public Flow.Publisher<ByteBuffer> asPublisher() {
        return HttpRequest.BodyPublishers.ofByteArray(bytes);
    }
}
