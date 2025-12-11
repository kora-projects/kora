package ru.tinkoff.kora.s3.client;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface S3ClientConfigWithCredentials extends S3ClientConfig {
    AwsCredentials credentials();
}
