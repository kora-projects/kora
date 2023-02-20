package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.database.symbol.processor.AbstractRepositoryTest

abstract class AbstractCassandraRepositoryTest : AbstractRepositoryTest() {
    val executor = MockCassandraExecutor()

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.cassandra.*;
            import ru.tinkoff.kora.database.cassandra.mapper.result.*;
            import ru.tinkoff.kora.database.cassandra.mapper.parameter.*;

            import java.util.concurrent.CompletionStage;
            import reactor.core.publisher.*;

            import com.datastax.oss.driver.api.core.cql.*;
            import com.datastax.oss.driver.api.core.data.*;
        """.trimIndent()
    }

    protected fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = compile(executor, arguments, *sources)
}
