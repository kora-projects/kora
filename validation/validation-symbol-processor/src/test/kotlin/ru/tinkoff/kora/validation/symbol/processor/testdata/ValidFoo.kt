package ru.tinkoff.kora.validation.symbol.processor.testdata

import ru.tinkoff.kora.validation.common.annotation.NotEmpty
import ru.tinkoff.kora.validation.common.annotation.Pattern
import ru.tinkoff.kora.validation.common.annotation.Range
import ru.tinkoff.kora.validation.common.annotation.Valid
import java.time.OffsetDateTime

@Valid
data class ValidFoo(
    @NotEmpty @Pattern("\\d+")
    val number: String,
    @Range(from = 1.0, to = 10.0)
    val code: Long,
    val timestamp: OffsetDateTime,
    @Valid
    val bar: ValidBar?
) {

    companion object {
        const val ignored: String = "ops"
    }
}
