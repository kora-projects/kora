package ru.tinkoff.kora.json.annotation.processor.reader;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public interface ReaderFieldType {

    enum JsonValueType {
        VALUE,
        NULLABLE,
        UNDEFINED
    }

    @Nullable
    JsonValueType jsonValueType();

    default boolean isJsonNullable() {
        return jsonValueType() != null
               && (jsonValueType() == ReaderFieldType.JsonValueType.VALUE || jsonValueType() == ReaderFieldType.JsonValueType.NULLABLE);
    }

    default boolean isJsonUndefined() {
        return jsonValueType() != null
               && (jsonValueType() == ReaderFieldType.JsonValueType.VALUE || jsonValueType() == JsonValueType.UNDEFINED);
    }

    TypeMirror typeMirror();

    record KnownTypeReaderMeta(KnownType.KnownTypesEnum knownType, TypeMirror typeMirror, JsonValueType jsonValueType) implements ReaderFieldType {}

    record UnknownTypeReaderMeta(TypeMirror typeMirror, JsonValueType jsonValueType) implements ReaderFieldType {}
}
