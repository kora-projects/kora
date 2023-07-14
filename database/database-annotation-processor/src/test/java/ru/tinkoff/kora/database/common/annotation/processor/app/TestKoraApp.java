package ru.tinkoff.kora.database.common.annotation.processor.app;

import org.mockito.Mockito;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

@KoraApp
public interface TestKoraApp {
    @Repository
    interface TestRepository extends JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        void abstractMethod(String value);
    }

    default JdbcConnectionFactory jdbcQueryExecutorAccessor() {
        return Mockito.mock(JdbcConnectionFactory.class);
    }

    @Root
    default Object mockLifecycle(TestRepository testRepository) {
        return new Object();
    }

}
