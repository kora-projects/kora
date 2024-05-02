package ru.tinkoff.kora.s3.client.model;

import java.util.List;

public interface S3ObjectList extends S3ObjectMetaList {

    List<S3Object> objects();

    @Override
    default List<S3ObjectMeta> objectMetas() {
        return objects().stream()
            .map(o -> ((S3ObjectMeta) o))
            .toList();
    }
}
