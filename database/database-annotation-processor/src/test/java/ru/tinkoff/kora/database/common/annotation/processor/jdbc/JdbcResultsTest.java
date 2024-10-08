package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Integer> test();
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
    public void testReturnMonoObjectWithExecutor() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository(executorTag = @Tag(String.class))
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Integer> test();
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
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testReturnCompletionStageObject() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Integer> test();
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
    public void testReturnCompletionStageObjectWithExecutor() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository(executorTag = @Tag(String.class))
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Integer> test();
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
    public void testReturnCompletionStageVoid() throws SQLException {
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testReturnCompletableFutureObject() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletableFuture<Integer> test();
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
    public void testReturnCompletableFutureObjectWithExecutor() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool(), mapper), """
            @Repository(executorTag = @Tag(String.class))
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletableFuture<Integer> test();
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
    public void testReturnCompletableFutureVoid() throws SQLException {
        var repository = compileJdbc(List.of(Executors.newCachedThreadPool()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletableFuture<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void returnUpdateCount() throws SQLException {
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
    public void returnBatchArbitraryFails() {
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            compileJdbc(List.of(), """
                @Repository
                public interface TestRepository extends JdbcRepository {
                    @Query("INSERT INTO test(value) VALUES (:value)")
                    java.util.List<String> test(@Batch java.util.List<String> value);
                }
                """);
        });

        assertThat(exception.getMessage()).contains("@Batch method can't return arbitrary values, it can only return: void/UpdateCount or database-generated @Id");
    }

    @Test
    public void returnBatchVoid() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                void test(@Batch java.util.List<String> value);
            }
            """);
        when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{42, 43});

        repository.<Void>invoke("test", List.of("test1", "test2"));
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES (?)");
        verify(executor.preparedStatement).executeBatch();
    }

    @Test
    public void returnBatchUpdateCount() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                UpdateCount test(@Batch java.util.List<String> value);
            }
            """);
        when(executor.preparedStatement.executeLargeBatch()).thenReturn(new long[]{42, 43});

        var result = repository.<UpdateCount>invoke("test", List.of("test1", "test2"));

        assertThat(result.value()).isEqualTo(85);
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES (?)");
        verify(executor.preparedStatement).executeLargeBatch();
    }

    @Test
    public void returnBatchIntArray() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:someint)")
                int[] returnVoidBatch(@Batch java.util.List<Integer> someint);
            }
            """);
        when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{1, 2, 3});
        var result = (int[]) repository.invoke("returnVoidBatch", List.of(1, 2, 3));

        verify(executor.preparedStatement).executeBatch();
        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    public void returnBatchLongArray() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:someint)")
                long[] returnVoidBatch(@Batch java.util.List<Integer> someint);
            }
            """);
        when(executor.preparedStatement.executeLargeBatch()).thenReturn(new long[]{1, 2, 3});
        var result = (long[]) repository.invoke("returnVoidBatch", List.of(1, 2, 3));

        verify(executor.preparedStatement).executeLargeBatch();
        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    public void returnBatchGeneratedIds() throws SQLException {
        var repository = compileJdbc(List.of(JdbcResultSetMapper.listResultSetMapper(rs -> rs.getLong(1))), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:someint)")
                @Id
                java.util.List<Long> returnIds(@Batch java.util.List<Integer> someint);
            }
            """);

        when(executor.resultSet.next()).thenReturn(true, true, true, false);
        when(executor.resultSet.getLong(anyInt())).thenReturn(1L, 2L, 3L);
        when(executor.mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(executor.preparedStatement);
        when(executor.preparedStatement.getGeneratedKeys()).thenReturn(executor.resultSet);
        var result = (List<Long>) repository.invoke("returnIds", List.of(1, 2, 3));

        verify(executor.preparedStatement).getGeneratedKeys();
        assertThat(result).containsExactly(1L, 2L, 3L);
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

    @Test
    public void testMultipleMethodsWithSameReturnType() {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                Integer test1();
                @Query("SELECT count(*) FROM test")
                Integer test2();
                @Query("SELECT count(*) FROM test")
                Integer test3();
            }
            """);
    }

    @Test
    public void testMultipleMethodsWithSameMapper() {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Integer test1();
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Integer test2();
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Integer test3();
            }
            """, """
            public class TestRowMapper implements JdbcRowMapper<Integer> {
                public Integer apply(ResultSet rs) {
                  return 42;
                }
            }
            """);
    }

    @Test
    public void testMethodsWithSameName() {
        var mapper1 = Mockito.mock(JdbcResultSetMapper.class);
        var mapper2 = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper1, mapper2), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Integer test(int test);
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Integer test(long test);
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Long test(String test);
            }
            """);
    }

    @Test
    public void testTagOnResultMapper() throws SQLException {
        var mapper = Mockito.mock(JdbcResultSetMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Tag(TestRepository.class)
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

        var mapperConstructorParameter = repository.objectClass.getConstructors()[0].getParameters()[1];
        assertThat(mapperConstructorParameter.getType()).isEqualTo(JdbcResultSetMapper.class);
        var tag = mapperConstructorParameter.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.value()).isEqualTo(new Class<?>[]{compileResult.loadClass("TestRepository")});
    }

}
