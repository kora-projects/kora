package io.koraframework.database.common.annotation.processor.jdbc;

import io.koraframework.database.common.annotation.processor.AbstractRepositoryTest;
import org.intellij.lang.annotations.Language;

import java.util.List;

public abstract class AbstractJdbcRepositoryTest extends AbstractRepositoryTest {
    protected MockJdbcExecutor executor = new MockJdbcExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.common.*;
            import io.koraframework.database.jdbc.*;
            import io.koraframework.database.jdbc.mapper.result.*;
            import io.koraframework.database.jdbc.mapper.parameter.*;
            import io.koraframework.common.Mapping;
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;

            import java.sql.*;
            import org.jspecify.annotations.Nullable;
            """;
    }

    protected TestObject compileJdbc(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
