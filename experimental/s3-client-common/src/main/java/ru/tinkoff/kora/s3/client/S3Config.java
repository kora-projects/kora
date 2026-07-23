package ru.tinkoff.kora.s3.client;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface S3Config {

    /**
     * @return URL of the S3 storage where requests will be sent.
     */
    String url();

    /**
     * @return S3 storage access key.
     */
    String accessKey();

    /**
     * @return S3 storage secret key.
     */
    String secretKey();

    /**
     * @return S3 storage region.
     */
    default String region() {
        return "aws-global";
    }

    /**
     * @return Telemetry configuration of the S3 client: logging, metrics and tracing.
     */
    TelemetryConfig telemetry();
}

