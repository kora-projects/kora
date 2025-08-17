package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.database.symbol.processor.RepositorySymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class JdbcAopTest : AbstractJdbcRepositoryTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.jdbc.*;
            import ru.tinkoff.kora.database.jdbc.mapper.result.*;
            import ru.tinkoff.kora.database.jdbc.mapper.parameter.*;

            import java.sql.*;
            
            import ru.tinkoff.kora.config.common.Config
            import ru.tinkoff.kora.config.common.factory.MapConfigFactory
            import ru.tinkoff.kora.logging.common.annotation.Log
        """.trimIndent()
    }

    fun compile(@Language("kotlin") vararg sources: String) {
        val classAsStrs = mutableListOf(
            """
                @ru.tinkoff.kora.common.Module
                interface ConfigModule : ru.tinkoff.kora.database.jdbc.JdbcDatabaseModule, ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule, ru.tinkoff.kora.logging.logback.LogbackModule {
                    fun config(): Config {
                        return MapConfigFactory.fromMap(
                            mapOf<String, Any>(
                                "test" to ""
                            )
                        );
                    }
                }
            """.trimIndent()
        )
        classAsStrs.addAll(sources)

        compile0(listOf(RepositorySymbolProcessorProvider(), AopSymbolProcessorProvider()), *classAsStrs.toTypedArray())
        compileResult.assertSuccess()
    }

    @Test
    fun testAopPreserved() {
        compile(
            """
            @Repository
            interface TestRepository : JdbcRepository {
                @Log
                @Query("INSERT INTO test(value) VALUES ('value')")
                fun test()
            }
            """.trimIndent()
        )
    }
}
