package io.koraframework.database.common.annotation.processor.app;

import org.mockito.Mockito;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcConnectionFactory;
import io.koraframework.database.jdbc.JdbcRepository;

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
