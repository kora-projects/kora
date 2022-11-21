package ru.tinkoff.kora.database.common.annotation.processor.app;

import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public interface TestKoraAppTagged {

    class ExampleTag {}

    @Repository(executorTag = @Tag(ExampleTag.class))
    interface TestRepository extends JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        Mono<Void> abstractMethod(String value);
    }

    @Tag(ExampleTag.class)
    default JdbcConnectionFactory jdbcQueryExecutorAccessor() {
        return Mockito.mock(JdbcConnectionFactory
            .class);
    }

    @Tag(ExampleTag.class)
    default Executor executor() {
        return Executors.newCachedThreadPool();
    }
}
