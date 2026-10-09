package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraph
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.Violation
import io.koraframework.validation.common.ViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ValidationTypeUseTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.common.annotation.Component
            import io.koraframework.common.annotation.KoraApp
            import io.koraframework.common.annotation.Root
            import io.koraframework.validation.common.Validator
            import io.koraframework.validation.common.annotation.*
            import io.koraframework.validation.common.constraint.ValidatorModule
            """.trimIndent() + "\n"
    }

    private val item = """
        @Valid
        data class Item(@field:Size(min = 1, max = 3) val name: String)
        """.trimIndent()

    @Test
    fun validatorChecksAnnotatedTypeArguments() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            item,
            """
            @Valid
            data class TestRecord(
                val names: List<@Size(min = 1, max = 3) String>,
                val items: Map<@NotBlank String, @Valid Item>,
                val nested: Map<String, List<@Valid Item>>,
            )
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ValidatorModule {
                @Root
                fun root(validator: Validator<TestRecord>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        loadClass("TestAppGraph").toGraph().use { graph ->
            @Suppress("UNCHECKED_CAST")
            val validator = graph.findByType(loadClass("\$TestRecord_Validator")) as Validator<Any>
            val validItem = new("Item", "ok")
            val invalidItem = new("Item", "too long")

            assertThat(validator.validate(new("TestRecord", listOf("ok"), mapOf("key" to validItem), mapOf("key" to listOf(validItem))))).isEmpty()

            assertThat(paths(validator.validate(new("TestRecord", listOf("ok", "too long"), mapOf<String, Any>(), mapOf<String, Any>()))))
                .containsExactly("names.[1]")
            assertThat(paths(validator.validate(new("TestRecord", listOf<String>(), mapOf(" " to validItem), mapOf<String, Any>()))))
                .containsExactly("items. ")
            assertThat(paths(validator.validate(new("TestRecord", listOf<String>(), mapOf("key" to invalidItem), mapOf<String, Any>()))))
                .containsExactly("items.key.name")
            assertThat(paths(validator.validate(new("TestRecord", listOf<String>(), mapOf<String, Any>(), mapOf("key" to listOf(validItem, invalidItem))))))
                .containsExactly("nested.key.[1].name")
        }
    }

    @Test
    fun validatorChecksValidMapValues() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            item,
            """
            @Valid
            data class TestRecord(@field:Valid val items: Map<String, Item>)
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ValidatorModule {
                @Root
                fun root(validator: Validator<TestRecord>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        loadClass("TestAppGraph").toGraph().use { graph ->
            @Suppress("UNCHECKED_CAST")
            val validator = graph.findByType(loadClass("\$TestRecord_Validator")) as Validator<Any>

            assertThat(validator.validate(new("TestRecord", mapOf("key" to new("Item", "ok"))))).isEmpty()
            assertThat(paths(validator.validate(new("TestRecord", mapOf("key" to new("Item", "too long"))))))
                .containsExactly("items.key.name")
        }
    }

    @Test
    fun validateAspectChecksAnnotatedTypeArguments() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            item,
            """
            @Component
            open class TestService {
                @Validate
                open fun call(names: List<@Size(min = 1, max = 3) String>, items: Map<String, @Valid Item>): List<@Size(min = 1, max = 3) String> = names
            }
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ValidatorModule {
                @Root
                fun root(service: TestService) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        loadClass("TestAppGraph").toGraph().use { graph ->
            val service = graph.findByType(loadClass("TestService"))!!
            val call = service.javaClass.getMethod("call", List::class.java, Map::class.java)

            assertThat(call.invoke(service, listOf("ok"), mapOf("key" to new("Item", "ok")))).isEqualTo(listOf("ok"))
            assertThatThrownBy { call.invoke(service, listOf("too long"), mapOf("key" to new("Item", "too long"))) }
                .hasCauseInstanceOf(ViolationException::class.java)
                .cause()
                .satisfies({ e -> assertThat(paths((e as ViolationException).violations)).containsExactly("names.[0]", "items.key.name") })
        }
    }

    private fun paths(violations: List<Violation>) = violations.map { it.path().full() }
}
