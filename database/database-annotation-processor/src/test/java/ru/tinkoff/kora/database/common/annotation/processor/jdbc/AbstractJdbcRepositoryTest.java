package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractRepositoryTest;

import java.util.List;

public abstract class AbstractJdbcRepositoryTest extends AbstractRepositoryTest {
    protected MockJdbcExecutor executor = new MockJdbcExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.jdbc.*;
            import ru.tinkoff.kora.database.jdbc.mapper.result.*;
            import ru.tinkoff.kora.database.jdbc.mapper.parameter.*;
            import ru.tinkoff.kora.common.Mapping;

            import java.sql.*;
            """;
    }

    protected TestRepository compileJdbc(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
