package ru.tinkoff.kora.s3.client.minio;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface MinioS3ClientConfig {

    @Nullable
    Duration requestTimeout();

    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        default long partSize() {
            return 1024 * 1024 * 50; // 50Mb
        }
    }
}
