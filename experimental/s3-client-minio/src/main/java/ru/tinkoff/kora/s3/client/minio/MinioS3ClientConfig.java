package ru.tinkoff.kora.s3.client.minio;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface MinioS3ClientConfig {

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
     * @return File upload configuration.
     */
    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        /**
         * @return Maximum file part size for a single file upload.
         */
        default Size partSize() {
            return Size.of(8, Size.Type.MiB);
        }
    }
}
