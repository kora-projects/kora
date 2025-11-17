package ru.tinkoff.kora.json.annotation.processor.writer;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public sealed interface WriterFieldType {

    enum JsonValueType {
        VALUE,
        NULLABLE,
        UNDEFINED
    }

    @Nullable
    JsonValueType jsonValueType();

    default boolean isJsonNullable() {
        return jsonValueType() != null
               && (jsonValueType() == JsonValueType.VALUE || jsonValueType() == JsonValueType.NULLABLE);
    }

    default boolean isJsonUndefined() {
        return  jsonValueType() != null
                && (jsonValueType() == JsonValueType.VALUE || jsonValueType() == JsonValueType.UNDEFINED);
    }

    TypeMirror typeMirror();

    record KnownWriterFieldType(KnownType.KnownTypesEnum knownType, TypeMirror typeMirror, JsonValueType jsonValueType) implements WriterFieldType {}

    record UnknownWriterFieldType(TypeMirror typeMirror, JsonValueType jsonValueType) implements WriterFieldType {}
}
