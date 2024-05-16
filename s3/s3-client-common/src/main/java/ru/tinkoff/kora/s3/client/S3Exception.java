package ru.tinkoff.kora.s3.client;

public class S3Exception extends RuntimeException {

    public S3Exception(String message) {
        super(message);
    }

    public S3Exception(Throwable cause) {
        super(cause);
    }
}
