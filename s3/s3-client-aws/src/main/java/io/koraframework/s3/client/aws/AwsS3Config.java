package io.koraframework.s3.client.aws;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryConfig;

import java.time.Duration;

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

    @ConfigValueExtractor
    interface S3Credentials {

        String accessKey();

        String secretKey();
    }

    enum ChecksumCalculation {
        WHEN_SUPPORTED,
        WHEN_REQUIRED
    }
}
