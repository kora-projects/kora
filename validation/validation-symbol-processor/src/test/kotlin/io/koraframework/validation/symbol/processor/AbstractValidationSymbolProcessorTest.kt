package io.koraframework.validation.symbol.processor

import io.koraframework.ksp.common.AbstractSymbolProcessorTest

abstract class AbstractValidationSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() +
            """
           import java.util.concurrent.CompletableFuture
           import java.util.concurrent.CompletionStage
           import io.koraframework.json.common.JsonNullable
           import io.koraframework.json.common.JsonUndefined
           import io.koraframework.json.common.JsonValue
           import org.jspecify.annotations.NonNull
           import org.jetbrains.annotations.NotNull
           import io.koraframework.common.KoraApp
           import io.koraframework.common.Component
           import io.koraframework.common.annotation.Root
           import io.koraframework.validation.common.annotation.*
           import io.koraframework.validation.common.Validator
           import io.koraframework.validation.common.constraint.ValidatorModule
           """.trimIndent()
    }
}
