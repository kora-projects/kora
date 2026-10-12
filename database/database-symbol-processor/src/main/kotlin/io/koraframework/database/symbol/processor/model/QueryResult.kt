package io.koraframework.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.MappingData

sealed interface QueryResult {
    val type: KSType

    data class SimpleResult(override val type: KSType) : QueryResult

    data class ResultWithMapper constructor(override val type: KSType, val mappingData: MappingData) : QueryResult
}
