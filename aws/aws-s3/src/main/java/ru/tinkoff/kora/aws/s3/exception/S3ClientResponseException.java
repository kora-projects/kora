package ru.tinkoff.kora.aws.s3.exception;

public class S3ClientResponseException extends S3ClientException {
    private final int httpCode;

    public S3ClientResponseException(int httpCode) {
        this.httpCode = httpCode;
    }

    public S3ClientResponseException(String message, int httpCode) {
        super(message);
        this.httpCode = httpCode;
    }

    public S3ClientResponseException(String message, Throwable cause, int httpCode) {
        super(message, cause);
        this.httpCode = httpCode;
    }

    public S3ClientResponseException(Throwable cause, int httpCode) {
        super(cause);
        this.httpCode = httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
