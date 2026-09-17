package io.koraframework.json.annotation.processor.reader;

import org.jspecify.annotations.Nullable;
import io.koraframework.json.annotation.processor.KnownType;

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
