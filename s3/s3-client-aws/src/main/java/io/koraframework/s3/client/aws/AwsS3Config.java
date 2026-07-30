package io.koraframework.s3.client.aws;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryConfig;

import java.time.Duration;

@ConfigMapper
public interface AwsS3Config {

    enum AddressStyle {
        PATH,
        VIRTUAL_HOSTED
    }

    String url();

    default String region() {
        return "aws-global";
    }

    default AddressStyle addressStyle() {
        return AddressStyle.PATH;
    }

    default Duration requestTimeout() {
        return Duration.ofSeconds(45);
    }

    default ChecksumCalculation checksumCalculationRequest() {
        return ChecksumCalculation.WHEN_REQUIRED;
    }

    default ChecksumCalculation checksumValidationResponse() {
        return ChecksumCalculation.WHEN_REQUIRED;
    }

    default boolean chunkedEncodingEnabled() {
        return true;
    }

    S3Credentials credentials();

    AwsS3ClientTelemetryConfig telemetry();

    @ConfigMapper
    interface S3Credentials {

        String accessKey();

        String secretKey();
    }

    enum ChecksumCalculation {
        WHEN_SUPPORTED,
        WHEN_REQUIRED
    }
}
