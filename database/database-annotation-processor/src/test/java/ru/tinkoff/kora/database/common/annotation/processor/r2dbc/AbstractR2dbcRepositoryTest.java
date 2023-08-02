package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractRepositoryTest;

import java.util.List;

public abstract class AbstractR2dbcRepositoryTest extends AbstractRepositoryTest {
    protected MockR2dbcExecutor executor = new MockR2dbcExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.r2dbc.*;
            import ru.tinkoff.kora.database.r2dbc.mapper.result.*;
            import ru.tinkoff.kora.database.r2dbc.mapper.parameter.*;
            import java.util.concurrent.CompletionStage;
            import reactor.core.publisher.*;

            import io.r2dbc.spi.*;
            """;
    }

    protected TestObject compileR2dbc(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
