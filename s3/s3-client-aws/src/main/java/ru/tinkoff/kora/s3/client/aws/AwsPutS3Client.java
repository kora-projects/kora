package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.annotation.ObjectPut;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface AwsPutS3Client {

    // arguments
    @ObjectPut
    void put1(String key, byte[] value);

    @ObjectPut
    void put2(String key, ByteBuffer value);

    @ObjectPut
    void put3(String key, InputStream value);

    @ObjectPut(key = "{key1}-{key2}")
    void put4(String key1, String key2, byte[] value);

    @ObjectPut
    void put5(String key, byte[] value, ObjectCannedACL acl);

    // return
    @ObjectPut
    void put1r(String key, byte[] value);

    @ObjectPut
    PutObjectResponse put2r(String key, byte[] value);
}
