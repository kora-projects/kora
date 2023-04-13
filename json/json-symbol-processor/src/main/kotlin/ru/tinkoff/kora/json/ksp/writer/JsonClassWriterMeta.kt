package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType

data class JsonClassWriterMeta(val classDeclaration: KSClassDeclaration, val fields: List<FieldMeta>) {
    data class FieldMeta(val fieldSimpleName: KSName, val jsonName: String, val type: KSType, val typeMeta: WriterFieldType?, val writer: KSType?, val accessor: String)
}
