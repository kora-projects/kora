package ru.tinkoff.kora.s3.client.minio;

import jakarta.annotation.Nullable;
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

    default AddressStyle addressStyle() {
        return AddressStyle.PATH;
    }

    @Nullable
    Duration requestTimeout();

    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        default Size partSize() {
            return Size.of(8, Size.Type.MiB);
        }
    }
}
