package ru.tinkoff.kora.aws.s3.exception;

public final class S3ClientUnknownException extends S3ClientException {
    public S3ClientUnknownException() {
    }

    public S3ClientUnknownException(String message) {
        super(message);
    }

    public S3ClientUnknownException(String message, Throwable cause) {
        super(message, cause);
    }

    public S3ClientUnknownException(Throwable cause) {
        super(cause);
    }
}

