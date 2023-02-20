package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractRepositoryTest;

import java.util.List;

public abstract class AbstractCassandraRepositoryTest extends AbstractRepositoryTest {
    protected MockCassandraExecutor executor = new MockCassandraExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.cassandra.*;
            import ru.tinkoff.kora.database.cassandra.mapper.result.*;
            import ru.tinkoff.kora.database.cassandra.mapper.parameter.*;

            import java.util.concurrent.CompletionStage;
            import reactor.core.publisher.*;

            import com.datastax.oss.driver.api.core.cql.*;
            import com.datastax.oss.driver.api.core.data.*;
            """;
    }

    protected TestRepository compileCassandra(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
