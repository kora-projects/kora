package io.koraframework.database.symbol.processor.jdbc

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.database.symbol.processor.RepositorySymbolProcessorProvider

class JdbcAopTest : AbstractJdbcRepositoryTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.database.jdbc.*;
            import io.koraframework.database.jdbc.mapper.result.*;
            import io.koraframework.database.jdbc.mapper.parameter.*;

            import java.sql.*;
            
            import io.koraframework.config.common.Config
            import io.koraframework.config.common.factory.MapConfigFactory
            import io.koraframework.logging.common.annotation.Log
        """.trimIndent()
    }

    fun compile(@Language("kotlin") vararg sources: String) {
        val classAsStrs = mutableListOf(
            """
                @io.koraframework.common.Module
                interface ConfigModule : io.koraframework.database.jdbc.JdbcDatabaseModule, io.koraframework.config.common.DefaultConfigExtractorsModule, io.koraframework.logging.logback.LogbackModule {
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
