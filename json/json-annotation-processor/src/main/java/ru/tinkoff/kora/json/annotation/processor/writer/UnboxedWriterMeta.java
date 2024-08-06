package ru.tinkoff.kora.json.annotation.processor.writer;

import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public record UnboxedWriterMeta(TypeMirror typeMirror, TypeElement typeElement, FieldMeta field) {
    public record FieldMeta(
        VariableElement field,
        TypeMirror typeMirror,
        ExecutableElement accessor
    ) {}
}
