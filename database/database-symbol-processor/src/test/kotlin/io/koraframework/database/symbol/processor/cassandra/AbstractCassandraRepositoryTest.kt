package io.koraframework.database.symbol.processor.cassandra

import io.koraframework.database.symbol.processor.AbstractRepositoryTest
import org.intellij.lang.annotations.Language

abstract class AbstractCassandraRepositoryTest : AbstractRepositoryTest() {
    val executor = MockCassandraExecutor()

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.database.cassandra.*;
            import io.koraframework.database.cassandra.annotation.*;
            import io.koraframework.database.cassandra.mapper.result.*;
            import io.koraframework.database.cassandra.mapper.parameter.*;

            import java.util.concurrent.CompletionStage;

            import com.datastax.oss.driver.api.core.cql.*;
            import com.datastax.oss.driver.api.core.data.*;
        """.trimIndent()
    }

    protected fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = compile(executor, arguments, *sources)
}
