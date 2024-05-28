package ru.tinkoff.kora.s3.client;

public class S3NotFoundException extends S3Exception {

    public S3NotFoundException(Throwable cause, String code, String message) {
        super(cause, code, message);
    }

    public static S3NotFoundException ofNoSuchKey(Throwable cause, String message) {
        return new S3NotFoundException(cause, "NoSuchKey", message);
    }

    public static S3NotFoundException ofNoSuchBucket(Throwable cause, String message) {
        return new S3NotFoundException(cause, "NoSuchBucket", message);
    }
}
