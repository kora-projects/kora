package ru.tinkoff.kora.aws.s3.exception;

import jakarta.annotation.Nullable;

public class S3ClientErrorException extends S3ClientResponseException {
    private final String errorCode;
    private final String errorMessage;
    @Nullable
    private final String requestId;

    public S3ClientErrorException(int httpCode, String errorCode, String errorMessage, @Nullable String requestId) {
        this(null, httpCode, errorCode, errorMessage, requestId);
    }

    public S3ClientErrorException(Throwable cause, int httpCode, String errorCode, String errorMessage, String requestId) {
        super("S3ClientError: httpCode=%d, requestId=%s, errorCode=%s, errorMessage=%s".formatted(httpCode, requestId, errorCode, errorMessage), cause, httpCode);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.requestId = requestId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }
}

