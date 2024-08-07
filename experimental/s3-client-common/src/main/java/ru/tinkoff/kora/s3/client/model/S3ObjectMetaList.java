package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * List of S3 Objects metadata
 */
@ApiStatus.Experimental
public interface S3ObjectMetaList {

    String prefix();

    List<S3ObjectMeta> metas();
}
