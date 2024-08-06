package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.TypeName;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public record UnboxedReaderMeta(TypeMirror typeMirror, TypeElement typeElement, FieldMeta field) {
    public record FieldMeta(VariableElement parameter, TypeName typeName) {}
}
