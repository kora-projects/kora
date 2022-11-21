package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

data class JsonClassReaderMeta(
    val type: KSType,
    val typeReference: KSTypeReference,
    val fields: List<FieldMeta>,
    val discriminatorField: String?,
    val isSealedStructure: Boolean
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is JsonClassReaderMeta && this.type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    data class FieldMeta(val parameter: KSValueParameter, val jsonName: String, val type: KSType, val typeMeta: ReaderFieldType, val reader: KSType?)
}
