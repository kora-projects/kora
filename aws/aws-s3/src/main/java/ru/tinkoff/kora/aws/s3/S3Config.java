package ru.tinkoff.kora.aws.s3;

import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface S3Config {

    String endpoint();

    default String region() {
        return "aws-global";
    }

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

    UploadConfig upload();

    @ConfigValueExtractor
    interface UploadConfig {

        /**
         * 5 MiB to 5 GiB. There is no minimum size limit on the last part of your multipart upload.
         */
        default Size partSize() {
            return Size.of(5, Size.Type.MiB);
        }

        /**
         * The chunk size must be at least 8 KB. We recommend a chunk size of at least 64 KB for better performance.
         */
        default Size chunkSize() {
            return Size.of(64, Size.Type.KiB);
        }

        /**
         * In general, when your object size reaches 100 MB, you should consider using multipart uploads instead of uploading the object in a single operation.
         */
        default Size singlePartUploadLimit() {
            return Size.of(100, Size.Type.MiB);
        }
    }
}
