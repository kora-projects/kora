package ru.tinkoff.kora.s3.client.minio.model;

import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectMetaList;

import java.util.List;

public class MinioS3ObjectMetaList implements S3ObjectMetaList {

    private final String prefix;
    private final List<S3ObjectMeta> metas;

    public MinioS3ObjectMetaList(String prefix, List<S3ObjectMeta> metas) {
        this.prefix = prefix;
        this.metas = metas;
    }

    @Override
    public String prefix() {
        return prefix;
    }
    @Override
    public List<S3ObjectMeta> metas() {
        return metas;
    }

    @Override
    public String toString() {
        return metas.toString();
    }
}
