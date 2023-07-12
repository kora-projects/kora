package ru.tinkoff.kora.json.annotation.processor.writer;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;

public record JsonClassWriterMeta(TypeMirror typeMirror, TypeElement typeElement, List<FieldMeta> fields) {

    enum IncludeType {
        ALWAYS,
        NON_NULL,
        NON_EMPTY;

        private static final IncludeType[] types = values();

        public static Optional<IncludeType> tryParse(String name) {
            for (IncludeType includeType : types) {
                if(includeType.name().equals(name)) {
                    return Optional.of(includeType);
                }
            }

            return Optional.empty();
        }
    }

    record FieldMeta(
        VariableElement field,
        TypeMirror typeMirror,
        WriterFieldType writerTypeMeta,
        String jsonName,
        IncludeType includeType,
        ExecutableElement accessor,
        @Nullable TypeMirror writer
    ) {}
}
