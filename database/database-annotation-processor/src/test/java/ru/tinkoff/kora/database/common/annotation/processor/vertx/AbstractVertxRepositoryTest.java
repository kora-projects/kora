package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.sqlclient.Tuple;
import org.intellij.lang.annotations.Language;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractRepositoryTest;

import java.util.List;

public abstract class AbstractVertxRepositoryTest extends AbstractRepositoryTest {
    protected MockVertxExecutor executor = new MockVertxExecutor();

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

    protected Tuple tupleMatcher(Tuple tuple) {
        return ArgumentMatchers.argThat(new ArgumentMatcher<>() {
            @Override
            public boolean matches(Tuple argument) {
                if (argument.size() != tuple.size()) {
                    return false;
                }
                for (int i = 0; i < tuple.size(); i++) {
                    if (!tuple.getValue(i).equals(argument.getValue(i))) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                return tuple.deepToString();
            }
        });
    }
}
