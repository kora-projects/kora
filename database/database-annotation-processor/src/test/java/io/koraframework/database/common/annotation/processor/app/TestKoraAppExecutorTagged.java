package io.koraframework.database.common.annotation.processor.app;

import io.koraframework.common.annotation.Tag;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcExecutor;
import io.koraframework.database.jdbc.JdbcRepository;
import org.mockito.Mockito;

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
    default JdbcExecutor jdbcQueryExecutorAccessor() {
        return Mockito.mock(JdbcExecutor
            .class);
    }

    @Tag(ExampleTag.class)
    default Executor executor() {
        return Executors.newCachedThreadPool();
    }
}
