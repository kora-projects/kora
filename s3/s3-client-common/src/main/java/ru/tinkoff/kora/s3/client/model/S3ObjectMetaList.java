package ru.tinkoff.kora.s3.client.model;

import java.util.List;

/**
 * List of S3 Objects metadata
 */
public interface S3ObjectMetaList {

    String prefix();

    List<S3ObjectMeta> metas();
}
