package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectMetaList;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.List;

final class AwsS3ObjectMetaList implements S3ObjectMetaList {

    private final ListObjectsV2Response response;
    private final List<S3ObjectMeta> metas;

    public AwsS3ObjectMetaList(ListObjectsV2Response response) {
        this.response = response;
        this.metas = response.contents().stream()
            .<S3ObjectMeta>map(AwsS3ObjectMeta::new)
            .toList();
    }

    @Override
    public String prefix() {
        return response.prefix();
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
