package ru.tinkoff.kora.json.annotation.processor.reader;

import ru.tinkoff.kora.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public interface ReaderFieldType {
    record KnownTypeReaderMeta(KnownType.KnownTypesEnum knownType, TypeMirror typeMirror) implements ReaderFieldType {}

    record UnknownTypeReaderMeta(TypeMirror typeMirror) implements ReaderFieldType {}
}
