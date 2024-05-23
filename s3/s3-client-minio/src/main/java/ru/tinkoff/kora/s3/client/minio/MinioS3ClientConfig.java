package ru.tinkoff.kora.s3.client.minio;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface MinioS3ClientConfig {

    @Nullable
    Duration requestTimeout();
}
