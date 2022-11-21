package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

sealed interface QueryResult {
    val type: KSType

    interface ReactiveResult

    data class SimpleResult(override val type: KSType) : QueryResult

    data class ResultWithMapper constructor(override val type: KSType, val mappingData: MappingData) : QueryResult

    data class SuspendResult(override val type: KSType, val result: QueryResult) : QueryResult, ReactiveResult {
        init {
            if (!(result is SimpleResult || result is ResultWithMapper)) {
                throw ProcessingErrorException("Invalid suspend type: $result", type.declaration)
            }
        }
    }
}
