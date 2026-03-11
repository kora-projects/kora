package io.koraframework.database.common.annotation.processor.app;

import org.mockito.Mockito;
import io.koraframework.common.KoraApp;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;
import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcConnectionFactory;
import io.koraframework.database.jdbc.JdbcRepository;

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

    default JdbcConnectionFactory jdbcQueryExecutorAccessor() {
        return Mockito.mock(JdbcConnectionFactory.class);
    }

    @Root
    default String rootTestString(@Tag(RepositoryTag.class) TestKoraAppRepoTagged.TestRepository testRepository) {
        return testRepository.test();
    }

}
