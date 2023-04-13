package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.TypeName;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public record JsonClassReaderMeta(TypeMirror typeMirror, TypeElement typeElement, List<FieldMeta> fields) {
    public record FieldMeta(VariableElement parameter, String jsonName, TypeName typeName, @Nullable ReaderFieldType typeMeta, @Nullable TypeMirror reader) {}
}
