package ru.tinkoff.kora.s3.client.minio;

import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectList;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectMetaList;

import java.util.List;

final class MinioS3ObjectList implements S3ObjectList {

    private final String prefix;
    private final List<S3Object> objects;

    public MinioS3ObjectList(S3ObjectMetaList metaList, List<S3Object> objects) {
        this.objects = objects;
        this.prefix = metaList.prefix();
    }

    @Override
    public List<S3Object> objects() {
        return objects;
    }

    @Override
    public String prefix() {
        return prefix;
    }

    @Override
    public List<S3ObjectMeta> metas() {
        return (List<S3ObjectMeta>) ((List) objects);
    }

    @Override
    public String toString() {
        return objects.toString();
    }
}
