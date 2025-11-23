package ru.tinkoff.kora.validation.symbol.processor

import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

abstract class AbstractValidationSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() +
            """
           import java.util.concurrent.CompletableFuture
           import java.util.concurrent.CompletionStage
           import ru.tinkoff.kora.json.common.JsonNullable
           import jakarta.annotation.Nonnull
           import ru.tinkoff.kora.common.KoraApp
           import ru.tinkoff.kora.common.Component
           import ru.tinkoff.kora.common.annotation.Root
           import ru.tinkoff.kora.validation.common.annotation.*
           import ru.tinkoff.kora.validation.common.Validator
           import ru.tinkoff.kora.validation.common.constraint.ValidatorModule
           """.trimIndent()
    }
}
