package io.koraframework.s3.client.kora;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface S3ClientConfigWithCredentials extends S3ClientConfig {
    S3Credentials credentials();
}
