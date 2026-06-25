package io.koraframework.s3.client.kora;

public interface S3ClientFactory {

    S3Client create(S3ClientConfig config);

    default S3Client create(String configPath, Class<?> clientImpl, S3ClientConfig config) {
        return this.create(config);
    }
}
