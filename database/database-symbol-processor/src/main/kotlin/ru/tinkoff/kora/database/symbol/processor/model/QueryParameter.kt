
package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

sealed interface QueryParameter {
    val name: String
    val type: KSType
    val variable: KSValueParameter

    data class ConnectionParameter(override val name: String, override val type: KSType, override val variable: KSValueParameter) : QueryParameter

    data class SimpleParameter(override val name: String, override val type: KSType, override val variable: KSValueParameter) : QueryParameter

    data class EntityParameter(override val name: String, override val type: KSType, override val variable: KSValueParameter, val entity: DbEntity) : QueryParameter

    data class BatchParameter(override val name: String, override val type: KSType, override val variable: KSValueParameter, val parameter: QueryParameter) : QueryParameter
}
