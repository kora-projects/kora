package io.koraframework.json.annotation.processor.reader;

import io.koraframework.json.annotation.processor.KnownType;

import javax.lang.model.type.TypeMirror;

public interface ReaderFieldType {

    boolean isJsonNullable();

    TypeMirror typeMirror();

    record KnownTypeReaderMeta(KnownType.KnownTypesEnum knownType, TypeMirror typeMirror, boolean isJsonNullable) implements ReaderFieldType {}

    record UnknownTypeReaderMeta(TypeMirror typeMirror, boolean isJsonNullable) implements ReaderFieldType {}
}
