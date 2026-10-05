package io.koraframework.s3.client.kora.impl;

import io.koraframework.s3.client.kora.S3ClientConfig;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UriHelperTest {

    @ParameterizedTest
    @CsvSource({
        "http://host:9000,   PATH,           http://host:9000/bucket/key",
        "http://host:9000/,  PATH,           http://host:9000/bucket/key",
        "http://host/s3,     PATH,           http://host/s3/bucket/key",
        "http://host/s3/,    PATH,           http://host/s3/bucket/key",
        "http://host:9000,   VIRTUAL_HOSTED, http://bucket.host:9000/key",
        "http://host:9000/,  VIRTUAL_HOSTED, http://bucket.host:9000/key",
        "http://host/s3,     VIRTUAL_HOSTED, http://bucket.host/s3/key",
        "http://host/s3/,    VIRTUAL_HOSTED, http://bucket.host/s3/key",
    })
    void endpointPathIsAppendedOnce(String endpoint, S3ClientConfig.AddressStyle addressStyle, String expected) {
        var uriHelper = new UriHelper(config(endpoint, addressStyle));

        assertThat(uriHelper.uri("bucket", "key", null)).hasToString(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "key,        /bucket/key",
        "a/b,        /bucket/a/b",
        "a//b,       /bucket/a//b",
        "dir/,       /bucket/dir/",
        "dir//,      /bucket/dir//",
        "/x,         /bucket//x",
        "//x,        /bucket///x",
        "p//leading, /bucket/p//leading",
        "a b/c+d,    /bucket/a%20b/c%2Bd",
    })
    void keyIsEncodedVerbatim(String key, String expectedPath) {
        var uriHelper = new UriHelper(config("http://host:9000", S3ClientConfig.AddressStyle.PATH));

        assertThat(uriHelper.uri("bucket", key, null).getRawPath()).isEqualTo(expectedPath);
    }

    private static S3ClientConfig config(String endpoint, S3ClientConfig.AddressStyle addressStyle) {
        return new S3ClientConfig() {
            @Override
            public String endpoint() {
                return endpoint;
            }

            @Override
            public AddressStyle addressStyle() {
                return addressStyle;
            }

            @Override
            public UploadConfig upload() {
                throw new UnsupportedOperationException();
            }

            @Override
            public S3ClientTelemetryConfig telemetry() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
