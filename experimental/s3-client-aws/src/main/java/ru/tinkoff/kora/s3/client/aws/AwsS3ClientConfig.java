package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.util.Size;
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

        default Size bufferSize() {
            return Size.of(32, Size.Type.MiB);
        }

        default Size partSize() {
            return Size.of(8, Size.Type.MiB);
        }
    }
}
