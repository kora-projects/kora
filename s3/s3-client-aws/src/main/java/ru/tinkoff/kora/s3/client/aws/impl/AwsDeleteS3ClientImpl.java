package ru.tinkoff.kora.s3.client.aws.impl;

import ru.tinkoff.kora.s3.client.S3ClientConfig;
import ru.tinkoff.kora.s3.client.aws.AwsDeleteS3Client;
import ru.tinkoff.kora.s3.client.aws.AwsDeletesS3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Collection;
import java.util.List;

public class AwsDeleteS3ClientImpl implements AwsDeleteS3Client {

    private final S3ClientConfig clientConfig;
    private final S3Client client;

    public AwsDeleteS3ClientImpl(S3ClientConfig clientConfig, S3Client client) {
        this.clientConfig = clientConfig;
        this.client = client;
    }

    @Override
    public void delete1(String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        client.deleteObject(request);
    }

    @Override
    public void delete2(String key1, String key2) {
        String key = key1 + "-" + key2;

        var request = DeleteObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        client.deleteObject(request);
    }

    @Override
    public void delete1r(String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        client.deleteObject(request);
    }

    @Override
    public DeleteObjectResponse delete2r(String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        return client.deleteObject(request);
    }
}
