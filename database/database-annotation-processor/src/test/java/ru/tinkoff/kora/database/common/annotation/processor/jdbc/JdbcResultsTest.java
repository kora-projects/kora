package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JdbcResultsTest extends AbstractJdbcRepositoryTest {
    @Test
    public void testReturnVoid() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                void test();
            }
            """);

        repository.invoke("test");
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES ('value')");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testReturnPrimitive() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                int test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        verify(mapper).apply(executor.resultSet);
    }

    @Test
    public void testReturnObject() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                Integer test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        verify(mapper).apply(executor.resultSet);
        executor.reset();

        when(mapper.apply(any())).thenReturn(null);
        assertThatThrownBy(() -> repository.invoke("test")).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testReturnNullableObject() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Nullable
                @Query("SELECT count(*) FROM test")
                Integer test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        verify(mapper).apply(executor.resultSet);
        executor.reset();


        when(mapper.apply(any())).thenReturn(null);
        result = repository.invoke("test");
        assertThat(result).isNull();
    }

    @Test
    public void testReturnMonoObject() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                reactor.core.publisher.Mono<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        verify(mapper).apply(executor.resultSet);
    }

    @Test
    public void testReturnMonoVoid() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                reactor.core.publisher.Mono<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testReturnUpdateCount() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                UpdateCount test();
            }
            """);
        when(executor.preparedStatement.executeLargeUpdate()).thenReturn(42L);

        var result = repository.<UpdateCount>invoke("test");

        assertThat(result.value()).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES ('test')");
        verify(executor.preparedStatement).executeLargeUpdate();
    }

    @Test
    public void testReturnBatchUpdateCount() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                UpdateCount test(@Batch java.util.List<String> value);
            }
            """);
        when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{42, 43});

        var result = repository.<UpdateCount>invoke("test", List.of("test1", "test2"));

        assertThat(result.value()).isEqualTo(85);
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES (?)");
        verify(executor.preparedStatement).executeBatch();
    }

    @Test
    public void testFinalResultSetMapper() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Integer test();
            }
            """, """
            public final class TestResultMapper implements JdbcResultSetMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testNonFinalFinalResultSetMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestResultMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Integer test();
            }
            """, """
            public class TestResultMapper implements JdbcResultSetMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testOneWithFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                @Nullable
                Integer test();
            }
            """, """
            public final class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, false);
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        executor.reset();

        when(executor.resultSet.next()).thenReturn(false, false);
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testOneWithNonFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                @Nullable
                Integer test();
            }
            """, """
            public class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, false);
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        executor.reset();

        when(executor.resultSet.next()).thenReturn(false, false);
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testOptionalWithFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Optional<Integer> test();
            }
            """, """
            public final class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, false);
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        executor.reset();

        when(executor.resultSet.next()).thenReturn(false, false);
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testOptionalWithNonFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Optional<Integer> test();
            }
            """, """
            public class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, false);
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        executor.reset();

        when(executor.resultSet.next()).thenReturn(false, false);
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testListWithFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                java.util.List<Integer> test();
            }
            """, """
            public final class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, true, false);
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
    }

    @Test
    public void testListWithNonFinalRowMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                java.util.List<Integer> test();
            }
            """, """
            public class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, true, false);
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).executeQuery();
        executor.reset();
    }
}
