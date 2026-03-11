package io.koraframework.database.common.annotation.processor.cassandra;

import io.koraframework.database.common.annotation.processor.AbstractRepositoryTest;
import org.intellij.lang.annotations.Language;

import java.util.List;

public abstract class AbstractCassandraRepositoryTest extends AbstractRepositoryTest {
    protected MockCassandraExecutor executor = new MockCassandraExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.database.cassandra.*;
            import io.koraframework.database.cassandra.mapper.result.*;
            import io.koraframework.database.cassandra.mapper.parameter.*;

            import java.util.concurrent.CompletionStage;

            import com.datastax.oss.driver.api.core.cql.*;
            import com.datastax.oss.driver.api.core.data.*;
            """;
    }

    protected TestObject compileCassandra(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
