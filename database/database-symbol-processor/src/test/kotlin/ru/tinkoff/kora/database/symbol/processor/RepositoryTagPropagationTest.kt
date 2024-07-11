package ru.tinkoff.kora.database.symbol.processor

import kotlin.reflect.KClass
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.jdbc.JdbcRepository
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest

class RepositoryTagPropagationTest : AbstractJdbcRepositoryTest() {

    @Test
    fun tagsFromInterfaceArePropagatedToImpl() {
        compile0(
            """
            @Repository
            @Tag(value = [TestRepository::class, JdbcRepository::class, Int::class])
            interface TestRepository : JdbcRepository {
                @Query("SELECT 1;")
                fun select1()
            }
            """.trimIndent(),
        )

        compileResult.assertSuccess()

        val repository = compileResult.loadClass("\$TestRepository_Impl").kotlin

        val repoInterfaceClass = repository.supertypes
            .map { it.classifier }
            .filterIsInstance<KClass<*>>()
            .first { it.simpleName == "TestRepository" }

        val tagClasses = repository.annotations
            .filterIsInstance<Tag>()
            .flatMap { it.value.toList() }

        Assertions.assertThat(tagClasses)
            .isNotEmpty
            .containsExactlyInAnyOrder(
                repoInterfaceClass,
                JdbcRepository::class,
                Int::class,
            )
    }
}
