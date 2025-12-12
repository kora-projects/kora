package ru.tinkoff.kora.s3.client.exception;

import jakarta.annotation.Nullable;

public class S3ClientNoSuchKeyException extends S3ClientErrorException {
    public S3ClientNoSuchKeyException(int httpCode, String errorCode, String errorMessage, @Nullable String requestId) {
        super(httpCode, errorCode, errorMessage, requestId);
    }
}
