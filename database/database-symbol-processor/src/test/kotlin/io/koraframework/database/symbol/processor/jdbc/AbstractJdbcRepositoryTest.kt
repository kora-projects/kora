package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.symbol.processor.AbstractRepositoryTest
import org.intellij.lang.annotations.Language

abstract class AbstractJdbcRepositoryTest : AbstractRepositoryTest() {
    val executor = MockJdbcExecutor()

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.database.jdbc.*;
            import io.koraframework.database.jdbc.mapper.result.*;
            import io.koraframework.database.jdbc.mapper.parameter.*;
            import io.koraframework.common.Mapping;

            import java.sql.*;
        """.trimIndent()
    }

    protected fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = compile(executor, arguments, *sources)
}
