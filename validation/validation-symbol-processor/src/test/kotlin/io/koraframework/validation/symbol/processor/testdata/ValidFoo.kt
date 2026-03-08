package io.koraframework.validation.symbol.processor.testdata

import io.koraframework.validation.common.annotation.NotEmpty
import io.koraframework.validation.common.annotation.Pattern
import io.koraframework.validation.common.annotation.Range
import io.koraframework.validation.common.annotation.Valid
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
