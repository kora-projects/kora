package ru.tinkoff.kora.json.ksp.reader

import com.squareup.kotlinpoet.TypeName
import ru.tinkoff.kora.json.ksp.KnownType

sealed interface ReaderFieldType {
    val type: TypeName

    data class KnownTypeReaderMeta(val knownType: KnownType.KnownTypesEnum, override val type: TypeName) : ReaderFieldType
    data class UnknownTypeReaderMeta(override val type: TypeName) : ReaderFieldType
}
