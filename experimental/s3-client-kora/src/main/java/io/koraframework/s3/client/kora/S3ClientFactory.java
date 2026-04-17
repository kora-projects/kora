package io.koraframework.s3.client.kora;

public interface S3ClientFactory {

    S3Client create(S3ClientConfig config);
}
