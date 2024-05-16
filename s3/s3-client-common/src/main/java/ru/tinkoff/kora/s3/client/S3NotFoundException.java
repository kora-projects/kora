package ru.tinkoff.kora.s3.client;

public class S3NotFoundException extends S3Exception {

    public S3NotFoundException(Throwable cause) {
        super(cause);
    }
}
