package ru.tinkoff.kora.json.annotation.processor.writer;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public record JsonClassWriterMeta(TypeMirror typeMirror, TypeElement typeElement, List<FieldMeta> fields, @Nullable String discriminatorField, boolean isSealedStructure) {

    record FieldMeta(
        VariableElement field,
        TypeMirror typeMirror,
        WriterFieldType writerTypeMeta,
        String jsonName,
        ExecutableElement accessor,
        @Nullable TypeMirror writer
    ) {}
}
