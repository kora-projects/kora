package ru.tinkoff.kora.s3.client;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface S3Config {

    String url();

    String accessKey();

    String secretKey();

    default String region() {
        return "aws-global";
    }

    TelemetryConfig telemetry();
}

