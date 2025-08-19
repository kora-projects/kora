package ru.tinkoff.kora.logging.symbol.processor.aop.mdc

import org.intellij.lang.annotations.Language
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider

class MdcKoraAspectUnsupportedTypesTest : AbstractMdcAspectTest() {

    @ParameterizedTest
    @MethodSource("sourcesWithMdcAndFuture", "sourcesWithMdcAndMono", "sourcesWithMdcAndFlux")
    fun testMdc(source: String) {
        val compileResult = compile0(
            listOf(AopSymbolProcessorProvider()),
            source
        )

        compileResult.assertFailure()
    }

    companion object {

        @JvmStatic
        @Language("kotlin")
        private fun sourcesWithMdcAndFuture() = listOf(
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String): CompletionStage<*> {
                    return CompletableFuture.completedFuture(1)
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key1", value = "value2")
                open fun test(s: String): CompletionStage<*> {
                    return CompletableFuture.completedFuture(1)
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {

                open fun test(@Mdc(key = "123") s: String): CompletionStage<*> {
                    return CompletableFuture.completedFuture(1)
                }
            }
        """
        )

        @JvmStatic
        @Language("kotlin")
        private fun sourcesWithMdcAndMono() = listOf(
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String): Mono<*> {
                    return Mono.just(1)
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key1", value = "value2")
                open fun test(s: String): Mono<*> {
                    return Mono.just(1)
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {

                open fun test(@Mdc(key = "123") s: String): Mono<*> {
                    return Mono.just(1)
                }
            }
        """
        )

        @JvmStatic
        @Language("kotlin")
        private fun sourcesWithMdcAndFlux() = listOf(
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String): Flux<*> {
                    return Mono.just(1)
                        .flux()
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key1", value = "value2")
                open fun test(s: String): Flux<*> {
                    return Mono.just(1)
                        .flux()
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {

                open fun test(@Mdc(key = "123") s: String): Flux<*> {
                    return Mono.just(1)
                        .flux()
                }
            }
        """
        )
    }
}
