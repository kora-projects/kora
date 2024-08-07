package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Uploaded S3 Object metadata
 */
@ApiStatus.Experimental
public interface S3ObjectUpload {

    String versionId();
}
