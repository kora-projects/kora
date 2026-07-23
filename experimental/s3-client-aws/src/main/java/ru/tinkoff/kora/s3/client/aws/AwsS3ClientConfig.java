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

    /**
     * @return Object access style, either PATH or VIRTUAL_HOSTED.
     */
    default AddressStyle addressStyle() {
        return AddressStyle.PATH;
    }

    /**
     * @return Maximum operation execution time.
     */
    default Duration requestTimeout() {
        return Duration.ofSeconds(45);
    }

    /**
     * @return Whether to check the MD5 checksum before upload and on retrieval from AWS.
     */
    default boolean checksumValidationEnabled() {
        return false;
    }

    /**
     * @return Whether to use chunked encoding when signing file data during upload to AWS.
     */
    default boolean chunkedEncodingEnabled() {
        return true;
    }

    /**
     * @return File upload configuration.
     */
    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        /**
         * @return Maximum buffer size for file uploads.
         */
        default Size bufferSize() {
            return Size.of(32, Size.Type.MiB);
        }

        /**
         * @return Maximum file part size for a single file upload.
         */
        default Size partSize() {
            return Size.of(8, Size.Type.MiB);
        }
    }
}
