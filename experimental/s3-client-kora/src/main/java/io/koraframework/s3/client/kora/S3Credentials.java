package io.koraframework.s3.client.kora;

import io.koraframework.s3.client.kora.impl.S3RequestSigner;

public interface S3Credentials {

    String accessKey();

    String secretKey();

    static S3Credentials of(String accessKey, String secretKey) {
        return new S3RequestSigner(accessKey, secretKey);
    }
}
