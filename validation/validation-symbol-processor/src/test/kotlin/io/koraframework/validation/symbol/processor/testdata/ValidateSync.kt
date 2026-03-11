package io.koraframework.validation.symbol.processor.testdata

import io.koraframework.validation.common.annotation.NotEmpty
import io.koraframework.validation.common.annotation.Pattern
import io.koraframework.validation.common.annotation.Range
import io.koraframework.validation.common.annotation.Size
import io.koraframework.validation.common.annotation.Valid
import io.koraframework.validation.common.annotation.Validate
import io.koraframework.common.Component

@Component
open class ValidateSync {

    companion object {
        const val ignored: String = "ops"
    }

    @Validate
    open fun validatedInput(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty @Pattern(".*") c2: String,
        @Valid c3: ValidTaz
    ): Int = c1

    @Validate
    open fun validatedInputVoid(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz?
    ) = Unit

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    open fun validatedOutput(
        c3: ValidTaz,
        c4: ValidTaz?
    ): List<ValidTaz>? = if (c4 == null) listOf(c3) else listOf(c3, c4)

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    open fun validatedInputAndOutput(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty @Pattern(".*") c2: String,
        @Valid c3: ValidTaz,
        c4: ValidTaz?
    ): List<ValidTaz>? = if (c4 == null) listOf(c3) else listOf(c3, c4)

    @Size(min = 1, max = 1)
    @Valid
    @Validate(failFast = true)
    open fun validatedInputAndOutputAndFailFast(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz,
        c4: ValidTaz?
    ): List<ValidTaz>? = if (c4 == null) listOf(c3) else listOf(c3, c4)
}
