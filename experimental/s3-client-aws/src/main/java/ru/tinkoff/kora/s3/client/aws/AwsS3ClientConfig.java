package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface AwsS3ClientConfig {

    enum AddressStyle {
        PATH,
        VIRTUAL_HOSTED
    }

    default AddressStyle addressStyle() {
        return AddressStyle.PATH;
    }

    default Duration requestTimeout() {
        return Duration.ofSeconds(45);
    }

    default boolean checksumValidationEnabled() {
        return false;
    }

    default boolean chunkedEncodingEnabled() {
        return true;
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
