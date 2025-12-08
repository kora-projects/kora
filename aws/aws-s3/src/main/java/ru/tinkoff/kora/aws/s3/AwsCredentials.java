package ru.tinkoff.kora.aws.s3;

import ru.tinkoff.kora.aws.s3.impl.AwsRequestSigner;

public interface AwsCredentials {
    String accessKey();

    String secretKey();

    static AwsCredentials of(String accessKey, String secretKey) {
        return new AwsRequestSigner(accessKey, secretKey);
    }
}
