package io.koraframework.json.ksp.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.MappingData

data class JsonClassWriterMeta(val type: KSClassDeclaration, val fields: List<FieldMeta>) {

    enum class IncludeType {
        ALWAYS, NON_NULL, NON_EMPTY;

        companion object {
            fun tryParse(name: String): IncludeType? {
                for (includeType in IncludeType.entries) {
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
        val typeMeta: WriterFieldType,
        val writer: MappingData?,
        val accessor: String,
        val includeType: IncludeType
    )
}
