package ru.tinkoff.kora.s3.client.model;

import java.time.Instant;

/**
 * S3 Object metadata representation
 */
public interface S3ObjectMeta {

    String key();

    Instant modified();

    long size();
}
