package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.annotation.ObjectList;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;

import java.util.List;

public interface AwsListS3Client {

    // arguments
    @ObjectList
    ListObjectsResponse list1(String prefix);

    @ObjectList(prefix = "key-{prefix1}-{prefix2}", limit = 10)
    ListObjectsResponse list2(String prefix1, String prefix2);

    // return
    @ObjectList
    List<String> list1r(String prefix);

    @ObjectList
    ListObjectsResponse list2r(String prefix);

    @ObjectList
    List<S3ObjectMeta> list3r(String prefix);

    @ObjectList
    List<S3Object> list4r(String prefix);
}
