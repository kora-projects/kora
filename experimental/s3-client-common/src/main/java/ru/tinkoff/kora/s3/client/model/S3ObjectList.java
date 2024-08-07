package ru.tinkoff.kora.s3.client.model;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * List of S3 Objects
 */
@ApiStatus.Experimental
public interface S3ObjectList extends S3ObjectMetaList {

    List<S3Object> objects();

    List<S3ObjectMeta> metas();
}
