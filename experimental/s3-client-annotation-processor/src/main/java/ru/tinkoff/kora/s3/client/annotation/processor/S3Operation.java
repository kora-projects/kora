package ru.tinkoff.kora.s3.client.annotation.processor;

import com.palantir.javapoet.CodeBlock;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

public record S3Operation(ExecutableElement method,
                          AnnotationMirror annotation,
                          OperationType type,
                          ImplType impl,
                          CodeBlock code) {

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
