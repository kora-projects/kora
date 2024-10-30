package ru.tinkoff.kora.json.annotation.processor.writer;

import ru.tinkoff.kora.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public sealed interface WriterFieldType {

    boolean isJsonNullable();

    TypeMirror typeMirror();

    record KnownWriterFieldType(KnownType.KnownTypesEnum knownType, TypeMirror typeMirror, boolean isJsonNullable) implements WriterFieldType {}

    record UnknownWriterFieldType(TypeMirror typeMirror, boolean isJsonNullable) implements WriterFieldType {}
}
