package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.common.UpdateCount;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JdbcMonoResultsTest extends AbstractJdbcRepositoryTest {
    @Test
    public void testReturnVoid() throws SQLException {
        var repository = compileJdbc(List.of(executor()), """
            @Repository(executorTag = @ru.tinkoff.kora.common.Tag(TestRepository.class))
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                reactor.core.publisher.Mono<Void> returnVoid();
            }
            """);

        var result = repository.invoke("returnVoid");

        assertThat(result).isNull();
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testReturnUpdateCount() throws SQLException {
        var repository = compileJdbc(List.of(executor()), """
            @Repository(executorTag = @ru.tinkoff.kora.common.Tag(TestRepository.class))
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (10)")
                reactor.core.publisher.Mono<UpdateCount> returnUpdateCount();
            }
            """);
        when(executor.preparedStatement.executeLargeUpdate()).thenReturn(42L);

        var updateCount = repository.<UpdateCount>invoke("returnUpdateCount");

        assertThat(updateCount.value()).isEqualTo(42);
        verify(executor.preparedStatement).executeLargeUpdate();
    }

    private Executor executor() {
        return Runnable::run;
    }
}
