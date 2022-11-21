package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference

data class JsonClassWriterMeta(
    val type: KSType,
    val typeReference: KSTypeReference,
    val fields: List<FieldMeta>,
    val discriminatorField: String?,
    val isSealedStructure: Boolean,
) {
    data class FieldMeta(val fieldSimpleName: KSName, val jsonName: String, val type: KSType, val typeMeta: WriterFieldType?, val writer: KSType?, val accessor: String)
}
