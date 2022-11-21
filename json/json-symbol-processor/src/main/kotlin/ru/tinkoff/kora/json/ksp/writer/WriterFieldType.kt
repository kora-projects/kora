package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import ru.tinkoff.kora.json.ksp.KnownType

sealed interface WriterFieldType{
    data class KnownWriterFieldType(val knownType: KnownType.KnownTypesEnum, val markedNullable: Boolean): WriterFieldType
    data class UnknownWriterFieldType(val type: KSTypeReference): WriterFieldType
}
