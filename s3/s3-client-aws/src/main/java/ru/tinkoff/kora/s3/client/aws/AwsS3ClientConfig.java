package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface AwsS3ClientConfig {

    default Duration requestTimeout() {
        return Duration.ofSeconds(15);
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
}
