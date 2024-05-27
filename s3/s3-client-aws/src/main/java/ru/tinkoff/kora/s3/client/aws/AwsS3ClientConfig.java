package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface AwsS3ClientConfig {

    default Duration requestTimeout() {
        return Duration.ofSeconds(45);
    }

    default boolean checksumValidationEnabled() {
        return false;
    }

    default boolean chunkedEncodingEnabled() {
        return true;
    }

    default boolean multiRegionEnabled() {
        return true;
    }

    default boolean pathStyleAccessEnabled() {
        return true;
    }

    default boolean accelerateModeEnabled() {
        return false;
    }

    default boolean useArnRegionEnabled() {
        return false;
    }

    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        default long bufferSize() {
            return 1024 * 1024 * 50; // 50 Mb
        }

        default long partSize() {
            return 1024 * 1024 * 25; // 25 Mb
        }
    }
}
