package ru.tinkoff.kora.s3.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public interface S3Body {

    default byte[] asBytes() {
        try (var stream = asInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream asInputStream();

    Flow.Publisher<ByteBuffer> asPublisher();

    long size();

    String encoding();

    String type();

    static S3Body ofBytes(byte[] body) {
        return new ByteS3Body(body, body.length, "application/octet-stream", null);
    }

    static S3Body ofBytes(byte[] body, String type) {
        return new ByteS3Body(body, body.length, type, null);
    }

    static S3Body ofBytes(byte[] body, String type, String encoding) {
        return new ByteS3Body(body, body.length, type, encoding);
    }

    static S3Body ofBuffer(ByteBuffer body) {
        return new ByteS3Body(body.array(), body.remaining(), null, null);
    }

    static S3Body ofBuffer(ByteBuffer body, String type) {
        return new ByteS3Body(body.array(), body.remaining(), type, null);
    }

    static S3Body ofBuffer(ByteBuffer body, String type, String encoding) {
        return new ByteS3Body(body.array(), body.remaining(), encoding, type);
    }

    static S3Body ofInputStreamUnbound(InputStream inputStream) {
        return ofInputStreamUnbound(inputStream, "application/octet-stream", null);
    }

    static S3Body ofInputStreamUnbound(InputStream inputStream, String type) {
        return ofInputStreamUnbound(inputStream, type, null);
    }

    static S3Body ofInputStreamUnbound(InputStream inputStream, String type, String encoding) {
        return new InputStreamS3Body(inputStream, -1, type, encoding);
    }

    static S3Body ofInputStreamReadAll(InputStream inputStream) {
        return ofInputStreamReadAll(inputStream, "application/octet-stream", null);
    }

    static S3Body ofInputStreamReadAll(InputStream inputStream, String type) {
        return ofInputStreamReadAll(inputStream, type, null);
    }

    static S3Body ofInputStreamReadAll(InputStream inputStream, String type, String encoding) {
        try (inputStream) {
            byte[] bytes = inputStream.readAllBytes();
            return new ByteS3Body(bytes, bytes.length, encoding, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static S3Body ofInputStream(InputStream inputStream, long size) {
        return ofInputStream(inputStream, size, "application/octet-stream", null);
    }

    static S3Body ofInputStream(InputStream inputStream, long size, String type) {
        return ofInputStream(inputStream, size, type, null);
    }

    static S3Body ofInputStream(InputStream inputStream, long size, String type, String encoding) {
        return new InputStreamS3Body(inputStream, size, type, encoding);
    }

    static S3Body ofPublisher(Flow.Publisher<ByteBuffer> publisher, long size) {
        return ofPublisher(publisher, size, "application/octet-stream", null);
    }

    static S3Body ofPublisher(Flow.Publisher<ByteBuffer> publisher, long size, String type) {
        return ofPublisher(publisher, size, type, null);
    }

    static S3Body ofPublisher(Flow.Publisher<ByteBuffer> publisher, long size, String type, String encoding) {
        return new PublisherS3Body(publisher, size, type, encoding);
    }
}
