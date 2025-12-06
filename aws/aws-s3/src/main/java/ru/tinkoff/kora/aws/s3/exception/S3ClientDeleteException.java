package ru.tinkoff.kora.aws.s3.exception;

import ru.tinkoff.kora.aws.s3.impl.xml.DeleteObjectsResult;

import java.util.List;

public class S3ClientDeleteException extends S3ClientException {
    private final List<DeleteObjectsResult.Error> errors;

    public S3ClientDeleteException(List<DeleteObjectsResult.Error> errors) {
        this.errors = errors;
    }

    public List<DeleteObjectsResult.Error> getErrors() {
        return errors;
    }
}
