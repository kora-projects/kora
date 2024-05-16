package ru.tinkoff.kora.s3.client.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public record ByteS3Body(byte[] bytes,
                         long size,
                         String type,
                         String encoding) implements S3Body {

    @Override
    public InputStream asInputStream() {
        return new ByteArrayInputStream(bytes);
    }
}
