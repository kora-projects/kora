package ru.tinkoff.kora.s3.client.model;

import java.time.Instant;

/**
 * S3 Object representation
 */
public interface S3Object {

    String key();

    Instant modified();

    long size();

    S3Body body();
}
