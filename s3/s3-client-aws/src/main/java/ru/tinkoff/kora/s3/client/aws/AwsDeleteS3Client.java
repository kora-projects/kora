package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.annotation.ObjectDelete;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

public interface AwsDeleteS3Client {

    // arguments
    @ObjectDelete
    void delete1(String key);

    @ObjectDelete(key = "{key1}-{key2}")
    void delete2(String key1, String key2);

    // return
    @ObjectDelete
    void delete1r(String key);

    @ObjectDelete
    DeleteObjectResponse delete2r(String key);
}
