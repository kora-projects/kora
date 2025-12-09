package ru.tinkoff.kora.database.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class RepositoryTagPropagationTest : AbstractJdbcRepositoryTest() {

    @Test
    fun tagsFromInterfaceArePropagatedToImpl() {
        compile0(
            listOf(RepositorySymbolProcessorProvider()),
            """
            @Repository
            @Tag(value = TestRepository::class)
            interface TestRepository : JdbcRepository {
                @Query("SELECT 1;")
                fun select1()
            }
            """.trimIndent(),
        )

        compileResult.assertSuccess()

        val repository = loadClass("\$TestRepository_Impl").kotlin

        val repoInterfaceClass = repository.supertypes
            .map { it.classifier }
            .filterIsInstance<KClass<*>>()
            .first { it.simpleName == "TestRepository" }

        val tagClasses = repository.findAnnotation<Tag>()?.value

        Assertions.assertThat(tagClasses).isEqualTo(repoInterfaceClass)
    }
}
