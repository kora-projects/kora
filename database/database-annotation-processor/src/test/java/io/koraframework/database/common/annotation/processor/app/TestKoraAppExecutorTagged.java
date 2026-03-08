package io.koraframework.database.common.annotation.processor.app;

import org.mockito.Mockito;
import io.koraframework.common.Tag;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcConnectionFactory;
import io.koraframework.database.jdbc.JdbcRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public interface TestKoraAppExecutorTagged {

    class ExampleTag {}

    @Repository(executorTag = ExampleTag.class)
    interface TestRepository extends JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        void abstractMethod(String value);
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
