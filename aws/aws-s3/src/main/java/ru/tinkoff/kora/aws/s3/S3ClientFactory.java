package ru.tinkoff.kora.aws.s3;

public interface S3ClientFactory {
    S3Client create(S3ClientConfig config);
}
