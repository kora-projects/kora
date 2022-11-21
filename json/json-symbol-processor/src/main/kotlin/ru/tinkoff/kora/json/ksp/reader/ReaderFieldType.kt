package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.symbol.KSTypeReference
import ru.tinkoff.kora.json.ksp.KnownType

sealed interface ReaderFieldType{
    val type: KSTypeReference
    data class KnownTypeReaderMeta(val knownType: KnownType.KnownTypesEnum, override val type: KSTypeReference): ReaderFieldType
    data class UnknownTypeReaderMeta(override val type: KSTypeReference): ReaderFieldType
}
