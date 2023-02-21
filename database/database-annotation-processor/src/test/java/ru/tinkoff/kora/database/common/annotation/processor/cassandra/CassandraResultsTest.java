package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.cql.Statement;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CassandraResultsTest extends AbstractCassandraRepositoryTest {
    @Test
    public void testReturnVoid() {
        var repository = compileCassandra(List.of(), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                void test();
            }
            """);

        repository.invoke("test");
        verify(executor.mockSession).prepare("INSERT INTO test(value) VALUES ('value')");
        verify(executor.mockSession).execute(any(Statement.class));
    }

    @Test
    public void testReturnPrimitive() {
        var mapper = Mockito.mock(CassandraResultSetMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("SELECT count(*) FROM test")
                int test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockSession).prepare("SELECT count(*) FROM test");
        verify(executor.mockSession).execute(any(Statement.class));
        verify(mapper).apply(executor.resultSet);
    }

    @Test
    public void testReturnObject() {
        var mapper = Mockito.mock(CassandraResultSetMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("SELECT count(*) FROM test")
                Integer test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockSession).prepare("SELECT count(*) FROM test");
        verify(executor.mockSession).execute(any(Statement.class));
        verify(mapper).apply(executor.resultSet);
    }

    @Test
    public void testReturnMonoObject() {
        var mapper = Mockito.mock(CassandraReactiveResultSetMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(Mono.just(42));
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test");
        verify(executor.mockSession).executeReactive(any(Statement.class));
        verify(mapper).apply(executor.reactiveResultSet);
    }

    @Test
    public void testReturnMonoVoid() {
        var repository = compileCassandra(List.of(), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test");
        verify(executor.mockSession).executeReactive(any(Statement.class));
    }

}
