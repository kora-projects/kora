package io.koraframework.database.common.annotation.processor.app;

import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcExecutor;
import io.koraframework.database.jdbc.JdbcRepository;
import org.mockito.Mockito;

@KoraApp
public interface TestKoraAppRepoTagged {

    class RepositoryTag {}

    @Repository
    @Tag(RepositoryTag.class)
    interface TestRepository extends JdbcRepository {
        @Query("INSERT INTO table(value) VALUES (:value)")
        void abstractMethod(String value);

        default String test() {
            return "i'm in tagged repo";
        }
    }

    default JdbcExecutor jdbcQueryExecutorAccessor() {
        return Mockito.mock(JdbcExecutor.class);
    }

    @Root
    default String rootTestString(@Tag(RepositoryTag.class) TestKoraAppRepoTagged.TestRepository testRepository) {
        return testRepository.test();
    }

}
