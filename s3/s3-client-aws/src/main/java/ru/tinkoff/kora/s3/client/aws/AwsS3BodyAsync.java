package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.model.S3Body;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class AwsS3BodyAsync implements S3Body {

    private final String encoding;
    private final String type;
    private final long size;
    private final Flow.Publisher<ByteBuffer> publisher;

    public AwsS3BodyAsync(String encoding, String type, long size, Flow.Publisher<ByteBuffer> publisher) {
        this.encoding = encoding;
        this.type = type;
        this.size = size;
        this.publisher = publisher;
    }

    @Override
    public InputStream asInputStream() {
        return S3Body.ofPublisher(publisher, size, type, encoding).asInputStream();
    }

    @Override
    public Flow.Publisher<ByteBuffer> asPublisher() {
        return publisher;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public String encoding() {
        return encoding;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String toString() {
        return "AwsS3Body{type=" + type +
            ", encoding=" + encoding +
            ", size=" + size +
            '}';
    }
}
