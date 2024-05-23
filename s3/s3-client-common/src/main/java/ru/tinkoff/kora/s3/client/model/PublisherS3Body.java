package ru.tinkoff.kora.s3.client.model;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public record PublisherS3Body(Flow.Publisher<ByteBuffer> publisher,
                              long size,
                              String type,
                              String encoding) implements S3Body {

    @Override
    public InputStream asInputStream() {
        try (var pub = new ByteBufferPublisher(publisher)) {
            return pub.asInputStream();
        }
    }

    @Override
    public Flow.Publisher<ByteBuffer> asPublisher() {
        return publisher;
    }
}
