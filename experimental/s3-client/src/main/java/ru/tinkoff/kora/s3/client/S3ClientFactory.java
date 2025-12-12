package ru.tinkoff.kora.s3.client;

public interface S3ClientFactory {
    S3Client create(S3ClientConfig config);
}
