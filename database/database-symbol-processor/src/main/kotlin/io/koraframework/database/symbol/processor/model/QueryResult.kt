package io.koraframework.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.MappingData
import io.koraframework.ksp.common.exception.ProcessingErrorException

sealed interface QueryResult {
    val type: KSType

    interface ReactiveResult

    data class SimpleResult(override val type: KSType) : QueryResult

    data class ResultWithMapper constructor(override val type: KSType, val mappingData: MappingData) : QueryResult

    data class SuspendResult(override val type: KSType, val result: QueryResult) : QueryResult, ReactiveResult {
        init {
            if (!(result is SimpleResult || result is ResultWithMapper)) {
                throw ProcessingErrorException(
                    """
                    Repository method has invalid suspend return type:
                      $type

                    Problem:
                      Suspend repository method resolved to unsupported result shape: $result

                    Hint:
                      Suspend methods must return a plain mapped value or a value with an explicit result mapper.

                    Fix:
                      Change the suspend method return type to a supported database result type, or provide an explicit mapper.
                    """.trimIndent(),
                    type.declaration
                )
            }
        }
    }
}
