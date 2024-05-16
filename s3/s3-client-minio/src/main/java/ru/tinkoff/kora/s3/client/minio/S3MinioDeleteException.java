package ru.tinkoff.kora.s3.client.minio;

import io.minio.messages.DeleteError;
import io.minio.messages.ErrorResponse;
import ru.tinkoff.kora.s3.client.S3Exception;

import java.util.List;
import java.util.stream.Collectors;

public class S3MinioDeleteException extends S3Exception {

    private final List<DeleteError> errors;

    public S3MinioDeleteException(List<DeleteError> errors) {
        super(new IllegalStateException(errors.stream()
            .map(ErrorResponse::message)
            .collect(Collectors.joining(", ", "Errors occurred while deleting objects: ", ""))));
        this.errors = errors;
    }

    public List<DeleteError> getErrors() {
        return errors;
    }
}
