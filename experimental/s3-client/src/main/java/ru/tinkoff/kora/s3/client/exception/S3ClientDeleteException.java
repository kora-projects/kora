package ru.tinkoff.kora.s3.client.exception;

import ru.tinkoff.kora.s3.client.impl.xml.DeleteObjectsResult;

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
