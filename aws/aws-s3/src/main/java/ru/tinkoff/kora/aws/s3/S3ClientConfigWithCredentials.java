package ru.tinkoff.kora.aws.s3;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface S3ClientConfigWithCredentials extends S3ClientConfig {
    AwsCredentials credentials();
}
