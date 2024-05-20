package ru.tinkoff.kora.s3.client;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface S3ClientConfig {

    String bucket();
}

