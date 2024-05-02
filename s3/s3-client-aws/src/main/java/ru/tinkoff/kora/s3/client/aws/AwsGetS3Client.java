package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.annotation.ObjectGet;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface AwsGetS3Client {

    // arguments
    @ObjectGet
    GetObjectResponse get1(String key);

    @ObjectGet(key = "{key1}-{key2}")
    GetObjectResponse get2(String key1, String key2);

    // return
    @ObjectGet
    byte[] get1r(String key);

    @ObjectGet
    ByteBuffer get2r(String key);

    @ObjectGet
    InputStream get3r(String key);

    @ObjectGet
    GetObjectResponse get4r(String key);

    @ObjectGet
    S3ObjectMeta get5r(String key);

    @ObjectGet
    S3Object get6r(String key);
}
