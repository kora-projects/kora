package ru.tinkoff.kora.s3.client.aws.impl;

import ru.tinkoff.kora.s3.client.S3ClientConfig;
import ru.tinkoff.kora.s3.client.aws.AwsDeleteS3Client;
import ru.tinkoff.kora.s3.client.aws.AwsGetS3Client;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class AwsGetS3ClientImpl implements AwsGetS3Client {

    private final S3ClientConfig clientConfig;
    private final S3Client client;

    public AwsGetS3ClientImpl(S3ClientConfig clientConfig, S3Client client) {
        this.clientConfig = clientConfig;
        this.client = client;
    }

    @Override
    public GetObjectResponse get1(String key) {
        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        return client.getObject(request).response();
    }

    @Override
    public GetObjectResponse get2(String key1, String key2) {
        String key = key1 + "-" + key2;

        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        return client.getObject(request).response();
    }

    @Override
    public byte[] get1r(String key) {
        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        try {
            return client.getObject(request).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ByteBuffer get2r(String key) {
        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        try {
            return ByteBuffer.wrap(client.getObject(request).readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream get3r(String key) {
        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        return client.getObject(request);
    }

    @Override
    public GetObjectResponse get4r(String key) {
        var request = GetObjectRequest.builder()
            .bucket(clientConfig.bucket())
            .key(key)
            .build();

        return client.getObject(request).response();
    }

    @Override
    public S3ObjectMeta get5r(String key) {
        return null;
    }

    @Override
    public S3Object get6r(String key) {
        return null;
    }
}
