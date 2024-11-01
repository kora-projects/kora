package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.TypeName
import ru.tinkoff.kora.json.ksp.KnownType

sealed interface ReaderFieldType {

    val typeName: TypeName

    val type: KSType

    val isJsonNullable: Boolean

    data class KnownTypeReaderMeta(
        override val type: KSType,
        override val typeName: TypeName,
        override val isJsonNullable: Boolean,
        val knownType: KnownType.KnownTypesEnum
    ) : ReaderFieldType

    data class UnknownTypeReaderMeta(
        override val type: KSType,
        override val typeName: TypeName,
        override val isJsonNullable: Boolean
    ) : ReaderFieldType
}
