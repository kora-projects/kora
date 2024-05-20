package ru.tinkoff.kora.s3.client.model;

import java.io.InputStream;

public record InputStreamS3Body(InputStream inputStream,
                                long size,
                                String type,
                                String encoding) implements S3Body {

    @Override
    public InputStream asInputStream() {
        return inputStream;
    }
}
