package io.koraframework.s3.client.kora.exception;

import io.koraframework.s3.client.kora.impl.xml.DeleteObjectsResult;

import java.util.List;
import java.util.stream.Collectors;

public class S3ClientDeleteException extends S3ClientException {

    private final List<DeleteObjectsResult.Error> errors;

    public S3ClientDeleteException(List<DeleteObjectsResult.Error> errors) {
        this.errors = errors;
    }

    public List<DeleteObjectsResult.Error> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        return "Delete failed for keyes due to: " + errors.stream()
            .map(e -> "- Failed with code '" + e.code() + "' due to: " + e.message())
            .collect(Collectors.joining("\n", "\n", ""));
    }
}
