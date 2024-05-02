package ru.tinkoff.kora.s3.client.aws.impl;

import ru.tinkoff.kora.s3.client.S3ClientConfig;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.aws.AwsDeletesS3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Collection;
import java.util.List;

public class AwsDeletesS3ClientImpl implements AwsDeletesS3Client {

    private final S3ClientConfig clientConfig;
    private final S3Client client;

    public AwsDeletesS3ClientImpl(S3ClientConfig clientConfig, S3Client client) {
        this.clientConfig = clientConfig;
        this.client = client;
    }

    @Override
    public void delete1(List<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(clientConfig.bucket())
            .delete(Delete.builder()
                .objects(cb -> {
                    for (String key : keys) {
                        cb.key(key);
                    }
                }).build())
            .build();

        client.deleteObjects(request);
    }

    @Override
    public void delete2(Collection<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(clientConfig.bucket())
            .delete(Delete.builder()
                .objects(cb -> {
                    for (String key : keys) {
                        cb.key(key);
                    }
                }).build())
            .build();

        client.deleteObjects(request);
    }

    @Override
    public void delete1r(List<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(clientConfig.bucket())
            .delete(Delete.builder()
                .objects(cb -> {
                    for (String key : keys) {
                        cb.key(key);
                    }
                }).build())
            .build();

        client.deleteObjects(request);
    }

    @Override
    public DeleteObjectsResponse delete2r(List<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(clientConfig.bucket())
            .delete(Delete.builder()
                .objects(cb -> {
                    for (String key : keys) {
                        cb.key(key);
                    }
                }).build())
            .build();

        return client.deleteObjects(request);
    }
}
