package io.koraframework.s3.client;

import io.koraframework.s3.client.impl.AwsRequestSigner;

public interface AwsCredentials {
    String accessKey();

    String secretKey();

    static AwsCredentials of(String accessKey, String secretKey) {
        return new AwsRequestSigner(accessKey, secretKey);
    }
}
