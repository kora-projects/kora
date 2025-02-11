package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.io.IOException;

/**
 * S3 Object representation
 */
@ApiStatus.Experimental
public record S3Object(S3ObjectMeta meta, S3Body body) implements Closeable {
    @Override
    public void close() throws IOException {
        this.body.close();
    }
}
