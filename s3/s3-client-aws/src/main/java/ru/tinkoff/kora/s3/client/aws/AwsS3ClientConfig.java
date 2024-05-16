package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface AwsS3ClientConfig {

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
        return false;
    }

    default boolean accelerateModeEnabled() {
        return false;
    }

    default boolean useArnRegionEnabled() {
        return false;
    }
}
