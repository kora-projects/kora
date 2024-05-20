package ru.tinkoff.kora.s3.client.model;

import java.util.List;

public interface S3ObjectList extends S3ObjectMetaList {

    List<S3Object> objects();

    List<S3ObjectMeta> metas();
}
