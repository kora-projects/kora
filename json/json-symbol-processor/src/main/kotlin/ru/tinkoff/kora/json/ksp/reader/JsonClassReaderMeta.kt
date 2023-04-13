package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.TypeName

data class JsonClassReaderMeta(val classDeclaration: KSClassDeclaration, val fields: List<FieldMeta>) {

    data class FieldMeta(val parameter: KSValueParameter, val jsonName: String, val type: TypeName, val typeMeta: ReaderFieldType, val reader: KSType?)
}
