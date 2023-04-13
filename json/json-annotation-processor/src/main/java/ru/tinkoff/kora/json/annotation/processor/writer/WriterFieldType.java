package ru.tinkoff.kora.json.annotation.processor.writer;

import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.json.annotation.processor.KnownType;

public sealed interface WriterFieldType {
    record KnownWriterFieldType(KnownType.KnownTypesEnum knownType) implements WriterFieldType {}

    record UnknownWriterFieldType(TypeName type) implements WriterFieldType {}
}
