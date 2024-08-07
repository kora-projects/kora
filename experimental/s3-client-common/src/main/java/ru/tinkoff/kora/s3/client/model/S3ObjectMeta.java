package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;

/**
 * S3 Object metadata representation
 */
@ApiStatus.Experimental
public interface S3ObjectMeta {

    String key();

    Instant modified();

    long size();
}
