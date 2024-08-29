package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectList;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectMetaList;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.List;

@ApiStatus.Experimental
final class AwsS3ObjectList implements S3ObjectList {

    private final S3ObjectMetaList metaList;
    private final List<S3Object> objects;

    public AwsS3ObjectList(ListObjectsV2Response response, List<S3Object> objects) {
        this.objects = objects;
        this.metaList = new AwsS3ObjectMetaList(response);
    }

    @Override
    public List<S3Object> objects() {
        return objects;
    }

    @Override
    public String prefix() {
        return metaList.prefix();
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
