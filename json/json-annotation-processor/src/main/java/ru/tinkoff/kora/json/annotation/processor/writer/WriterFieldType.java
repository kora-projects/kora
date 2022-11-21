package ru.tinkoff.kora.json.annotation.processor.writer;

import ru.tinkoff.kora.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public sealed interface WriterFieldType {
    record KnownWriterFieldType(KnownType.KnownTypesEnum knownType) implements WriterFieldType {}

    record UnknownWriterFieldType(TypeMirror type) implements WriterFieldType {}
}
