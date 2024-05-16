package ru.tinkoff.kora.s3.client;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface S3Config {

    String url();

    String accessKey();

    String secretKey();

    default String region() {
        return "us-east-1";
    }
}

