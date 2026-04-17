package io.koraframework.s3.client.kora.exception;

public abstract class S3ClientException extends RuntimeException {

    public S3ClientException() {}

    public S3ClientException(String message) {
        super(message);
    }

    public S3ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public S3ClientException(Throwable cause) {
        super(cause);
    }
}
