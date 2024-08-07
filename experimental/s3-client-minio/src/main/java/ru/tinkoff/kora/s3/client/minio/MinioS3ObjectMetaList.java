package ru.tinkoff.kora.s3.client.minio;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectMetaList;

import java.util.List;

@ApiStatus.Experimental
record MinioS3ObjectMetaList(String prefix, List<S3ObjectMeta> metas) implements S3ObjectMetaList {

    @Override
    public String toString() {
        return metas.toString();
    }
}
