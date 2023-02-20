package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.database.symbol.processor.AbstractRepositoryTest

abstract class AbstractJdbcRepositoryTest : AbstractRepositoryTest() {
    val executor = MockJdbcExecutor()

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.jdbc.*;
            import ru.tinkoff.kora.database.jdbc.mapper.result.*;
            import ru.tinkoff.kora.database.jdbc.mapper.parameter.*;
            import ru.tinkoff.kora.common.Mapping;

            import java.sql.*;
        """.trimIndent()
    }

    protected fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = compile(executor, arguments, *sources)
}
