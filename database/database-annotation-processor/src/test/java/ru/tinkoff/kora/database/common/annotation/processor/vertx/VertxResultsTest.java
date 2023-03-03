package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VertxResultsTest extends AbstractVertxRepositoryTest {

    @Test
    public void testReturnCompletionStageObject() {
        var mapper = Mockito.mock(VertxRowSetMapper.class);
        var repository = compileVertx(List.of(mapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        verify(mapper).apply(executor.rowSet);
    }

    @Test
    public void testReturnCompletionStageVoid() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testReturnCompletionStageUpdateCount() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                CompletionStage<UpdateCount> test();
            }
            """);
        when(executor.rowSet.rowCount()).thenReturn(42);

        var result = repository.<UpdateCount>invoke("test");

        assertThat(result.value()).isEqualTo(42);
        verify(executor.connection).preparedQuery("INSERT INTO test(value) VALUES ('test')");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testReturnMonoObject() {
        var mapper = Mockito.mock(VertxRowSetMapper.class);
        var repository = compileVertx(List.of(mapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        verify(mapper).apply(executor.rowSet);
    }

    @Test
    public void testReturnMonoVoid() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testReturnUpdateCount() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                Mono<UpdateCount> test();
            }
            """);
        when(executor.rowSet.rowCount()).thenReturn(42);

        var result = repository.<UpdateCount>invoke("test");

        assertThat(result.value()).isEqualTo(42);
        verify(executor.connection).preparedQuery("INSERT INTO test(value) VALUES ('test')");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testReturnBatchUpdateCount() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                Mono<UpdateCount> test(@Batch java.util.List<String> value);
            }
            """);
        when(executor.rowSet.rowCount()).thenReturn(42);

        var result = repository.<UpdateCount>invoke("test", List.of("test1", "test2"));

        assertThat(result.value()).isEqualTo(42);
        verify(executor.connection).preparedQuery("INSERT INTO test(value) VALUES ($1)");
        verify(executor.query).executeBatch(any(), any());
    }

    @Test
    public void testFinalResultSetMapper() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Mono<Integer> test();
            }
            """, """
            public final class TestResultMapper implements VertxRowSetMapper<Integer> {
                public Integer apply(RowSet<Row> rs) {
                  return 42;
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testNonFinalFinalResultSetMapper() {
        var repository = compileVertx(List.of(newGeneratedObject("TestResultMapper")), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Mono<Integer> test();
            }
            """, """
            public class TestResultMapper implements VertxRowSetMapper<Integer> {
                public Integer apply(RowSet<Row> rs) {
                  return 42;
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testOneWithFinalRowMapper() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Integer> test();
            }
            """, """
            public final class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockVertxExecutor.MockColumn("count", 0));
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        executor.reset();

        executor.setRows(List.of());
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testOneWithNonFinalRowMapper() {
        var repository = compileVertx(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Integer> test();
            }
            """, """
            public class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockVertxExecutor.MockColumn("count", 0));
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        executor.reset();

        executor.setRows(List.of());
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testOptionalWithFinalRowMapper() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Optional<Integer>> test();
            }
            """, """
            public final class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockVertxExecutor.MockColumn("count", 0));
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        executor.reset();

        executor.setRows(List.of());
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testOptionalWithNonFinalRowMapper() {
        var repository = compileVertx(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Optional<Integer>> test();
            }
            """, """
            public class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockVertxExecutor.MockColumn("count", 0));
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        executor.reset();

        executor.setRows(List.of());
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testListWithFinalRowMapper() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<java.util.List<Integer>> test();
            }
            """, """
            public final class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRows(List.of(
            List.of(new MockVertxExecutor.MockColumn("count", 0)),
            List.of(new MockVertxExecutor.MockColumn("count", 0))
        ));
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
    }

    @Test
    public void testListWithNonFinalRowMapper() {
        var repository = compileVertx(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<java.util.List<Integer>> test();
            }
            """, """
            public class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRows(List.of(
            List.of(new MockVertxExecutor.MockColumn("count", 0)),
            List.of(new MockVertxExecutor.MockColumn("count", 0))
        ));
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test");
        verify(executor.query).execute(any(), any());
        executor.reset();
    }


    @Test
    public void testMultipleMethodsWithSameReturnType() {
        var mapper = Mockito.mock(VertxRowSetMapper.class);
        var repository = compileVertx(List.of(mapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
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
        var repository = compileVertx(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends VertxRepository {
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
            public class TestRowMapper implements VertxRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);
    }

    @Test
    public void testMethodsWithSameName() {
        var mapper1 = Mockito.mock(VertxRowSetMapper.class);
        var mapper2 = Mockito.mock(VertxRowSetMapper.class);
        var repository = compileVertx(List.of(mapper1, mapper2), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Integer test(int test);
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Integer test(long test);
                @Query("SELECT count(*) FROM test WHERE test = :test")
                Long test(String test);
            }
            """);

    }

}
