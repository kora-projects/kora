package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractRepositoryTest;

import java.util.List;

public abstract class AbstractVertxRepositoryTest extends AbstractRepositoryTest {
    protected MockVertxExecutor executor;

    @Override
    protected String commonImports() {
        return """
            import ru.tinkoff.kora.database.common.annotation.*;
            import ru.tinkoff.kora.database.common.*;
            import ru.tinkoff.kora.database.vertx.*;
            import ru.tinkoff.kora.database.vertx.mapper.result.*;
            import ru.tinkoff.kora.database.vertx.mapper.parameter.*;
            import java.util.concurrent.CompletionStage;
            import reactor.core.publisher.*;
            import io.vertx.sqlclient.SqlConnection;
            import io.vertx.sqlclient.SqlClient;
            """;
    }

    protected TestRepository compileVertx(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
