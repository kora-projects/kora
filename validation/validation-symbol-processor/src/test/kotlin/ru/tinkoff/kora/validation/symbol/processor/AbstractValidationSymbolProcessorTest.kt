package ru.tinkoff.kora.validation.symbol.processor

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.KotlinCompilation

abstract class AbstractValidationSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() +
            """
           import reactor.core.publisher.Flux
           import reactor.core.publisher.Mono
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

    fun compile(@Language("kotlin") vararg sources: String) = KotlinCompilation()
        .withClasspathJar("reactor-core")
        .compile(listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()), *sources)
}
