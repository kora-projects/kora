package io.koraframework.json.ksp.writer

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.TypeName
import io.koraframework.json.ksp.KnownType

sealed interface WriterFieldType {

    val typeName: TypeName

    val type: KSType

    val isJsonNullable: Boolean

    data class KnownWriterFieldType(override val type: KSType, override val typeName: TypeName, override val isJsonNullable: Boolean, val knownType: KnownType.KnownTypesEnum) : WriterFieldType

    data class UnknownWriterFieldType(override val type: KSType, override val typeName: TypeName, override val isJsonNullable: Boolean) : WriterFieldType
}
