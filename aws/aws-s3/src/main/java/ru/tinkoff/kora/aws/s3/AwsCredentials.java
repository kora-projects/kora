package ru.tinkoff.kora.aws.s3;

import ru.tinkoff.kora.aws.s3.impl.AwsRequestSigner;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface AwsCredentials {
    String accessKey();

    String secretKey();

    static AwsCredentials of(String accessKey, String secretKey) {
        return new AwsRequestSigner(accessKey, secretKey);
    }
}
