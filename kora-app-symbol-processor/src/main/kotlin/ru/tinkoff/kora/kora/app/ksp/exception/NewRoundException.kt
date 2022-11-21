package ru.tinkoff.kora.kora.app.ksp.exception

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.kora.app.ksp.ProcessingState

data class NewRoundException(
    val resolving: ProcessingState.Processing,
    val source: Any,
    val type: KSType,
    val tag: Set<String>
) : RuntimeException() {
    override fun fillInStackTrace(): Throwable {
        return this
    }
}
