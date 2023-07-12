package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType

data class JsonClassWriterMeta(val type: KSClassDeclaration, val fields: List<FieldMeta>) {

    enum class IncludeType {
        ALWAYS, NON_NULL, NON_EMPTY;

        companion object {
            private val types = IncludeType.values()

            fun tryParse(name: String): IncludeType? {
                for (includeType in IncludeType.Companion.types) {
                    if (includeType.name == name) {
                        return includeType
                    }
                }
                return null
            }
        }
    }

    data class FieldMeta(
        val fieldSimpleName: KSName,
        val jsonName: String,
        val type: KSType,
        val typeMeta: WriterFieldType?,
        val writer: KSType?,
        val accessor: String,
        val includeType: IncludeType
    )
}
