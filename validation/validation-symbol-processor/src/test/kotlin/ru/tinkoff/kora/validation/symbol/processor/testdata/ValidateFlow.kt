package ru.tinkoff.kora.validation.symbol.processor.testdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.validation.common.annotation.*

@Component
open class ValidateFlow {

    companion object {
        const val ignored: String = "ops"
    }

    @Validate
    open fun validatedInput(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz
    ): Flow<Int> = flow { emit(c1) }

    @Validate
    open fun validatedInputVoid(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz?
    ): Flow<Unit> = emptyFlow()

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    open fun validatedOutput(
        c3: ValidTaz,
        c4: ValidTaz?
    ): Flow<List<ValidTaz>?> {
        return if (c4 == null)
            flow { emit(listOf(c3)) }
        else
            flow { emit(listOf(c3, c4)) }
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    open suspend fun validatedInputAndOutput(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz,
        c4: ValidTaz?
    ): Flow<List<ValidTaz>?> {
        return if (c4 == null)
            flow { emit(listOf(c3)) }
        else
            flow { emit(listOf(c3, c4)) }
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate(failFast = true)
    open fun validatedInputAndOutputAndFailFast(
        @Range(from = 1.0, to = 5.0) c1: Int,
        @NotEmpty c2: String,
        @Valid c3: ValidTaz,
        c4: ValidTaz?
    ): Flow<List<ValidTaz>?> {
        return if (c4 == null)
            flow { emit(listOf(c3)) }
        else
            flow { emit(listOf(c3, c4)) }
    }
}
