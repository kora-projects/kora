package ru.tinkoff.kora.s3.client;

import java.util.List;
import java.util.stream.Collectors;

public class S3DeleteException extends S3Exception {

    public record Error(String key, String bucket, String code, String message) {}

    private final List<Error> errors;

    public S3DeleteException(List<Error> errors) {
        super(new IllegalStateException(errors.stream()
                .map(Error::message)
                .collect(Collectors.joining(", ", "Errors occurred while deleting objects: ", ""))),
            errors.get(0).code(),
            errors.get(0).message());
        this.errors = errors;
    }

    public List<Error> getErrors() {
        return errors;
    }
}
