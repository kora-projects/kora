package ru.tinkoff.kora.s3.client.annotation.processor;

import com.squareup.javapoet.CodeBlock;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

public record S3Operation(ExecutableElement method,
                          AnnotationMirror annotation,
                          OperationType type,
                          ImplType impl,
                          Mode mode,
                          CodeBlock code) {

    public enum Mode {
        ASYNC,
        SYNC
    }

    public enum OperationType {
        GET,
        LIST,
        PUT,
        DELETE
    }

    public enum ImplType {
        SIMPLE,
        AWS,
        MINIO
    }
}
