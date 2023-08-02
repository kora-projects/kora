package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.MockR2dbcExecutor.MockColumn;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class R2dbcResultsTest extends AbstractR2dbcRepositoryTest {

    @Test
    public void testReturnMonoObject() {
        var mapper = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(Mono.just(42));
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        verify(mapper).apply(any());
    }

    @Test
    public void testReturnMonoOptional() {
        var mapper = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Optional<Integer>> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(Mono.just(Optional.of(42)));
        var result = repository.<Optional<Integer>>invoke("test");

        assertThat(result).contains(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        verify(mapper).apply(any());

        when(mapper.apply(any())).thenReturn(Mono.just(Optional.empty()));
        executor.reset();
        executor.setRows(List.of());
        result = repository.invoke("test");
        assertThat(result).isEmpty();
    }

    @Test
    public void testReturnMonoVoid() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                Mono<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testReturnUpdateCount() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                Mono<UpdateCount> test();
            }
            """);
        executor.setUpdateCountResult(42);

        var result = repository.<UpdateCount>invoke("test");

        assertThat(result.value()).isEqualTo(42);
        verify(executor.con).createStatement("INSERT INTO test(value) VALUES ('test')");
        verify(executor.statement).execute();
    }

    @Test
    public void testReturnBatchUpdateCount() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                Mono<UpdateCount> test(@ru.tinkoff.kora.database.common.annotation.Batch java.util.List<String> value);
            }
            """);
        executor.setUpdateCountResult(42);

        var result = repository.<UpdateCount>invoke("test", List.of("test1", "test2"));

        assertThat(result.value()).isEqualTo(42);
        verify(executor.con).createStatement("INSERT INTO test(value) VALUES ($1)");
        verify(executor.statement).execute();
    }

    @Test
    public void testFinalResultSetMapper() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Mono<Integer> test();
            }
            """, """
            public final class TestResultMapper implements R2dbcResultFluxMapper<Integer, Mono<Integer>> {
                public Mono<Integer> apply(Flux<Result> rs) {
                  return Mono.just(42);
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testNonFinalFinalResultSetMapper() {
        var repository = compileR2dbc(List.of(newGeneratedObject("TestResultMapper")), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper.class)
                Mono<Integer> test();
            }
            """, """
            public class TestResultMapper implements R2dbcResultFluxMapper<Integer, Mono<Integer>> {
                public Mono<Integer> apply(Flux<Result> rs) {
                  return Mono.just(42);
                }
            }
            """);

        var result = repository.<Integer>invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testOneWithFinalRowMapper() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Integer> test();
            }
            """, """
            public final class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockColumn("count", 0));
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        executor.reset();

        executor.setRows(List.of());
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testOneWithNonFinalRowMapper() {
        var repository = compileR2dbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Integer> test();
            }
            """, """
            public class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockColumn("count", 0));
        var result = repository.<Integer>invoke("test");
        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        executor.reset();

        executor.setRows(List.of());
        result = repository.<Integer>invoke("test");
        assertThat(result).isNull();
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testOptionalWithFinalRowMapper() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Optional<Integer>> test();
            }
            """, """
            public final class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockColumn("count", 0));
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        executor.reset();

        executor.setRows(List.of());
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testOptionalWithNonFinalRowMapper() {
        var repository = compileR2dbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<Optional<Integer>> test();
            }
            """, """
            public class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRow(new MockColumn("count", 0));
        var result = repository.<Optional<Integer>>invoke("test");
        assertThat(result).contains(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        executor.reset();

        executor.setRows(List.of());
        result = repository.invoke("test");
        assertThat(result).isEmpty();
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testListWithFinalRowMapper() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<java.util.List<Integer>> test();
            }
            """, """
            public final class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRows(List.of(
            List.of(new MockColumn("count", 0)),
            List.of(new MockColumn("count", 0))
        ));
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testListWithNonFinalRowMapper() {
        var repository = compileR2dbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper.class)
                Mono<java.util.List<Integer>> test();
            }
            """, """
            public class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);

        executor.setRows(List.of(
            List.of(new MockColumn("count", 0)),
            List.of(new MockColumn("count", 0))
        ));
        var result = repository.<List<Integer>>invoke("test");
        assertThat(result).contains(42, 42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        executor.reset();
    }

    @Test
    public void testMultipleMethodsWithSameReturnType() {
        var mapper = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
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
        var repository = compileR2dbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
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
            public class TestRowMapper implements R2dbcRowMapper<Integer> {
                public Integer apply(Row row) {
                  return 42;
                }
            }
            """);
    }

    @Test
    public void testMethodsWithSameName() {
        var mapper1 = Mockito.mock(R2dbcResultFluxMapper.class);
        var mapper2 = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper1, mapper2), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
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
    public void testTagOnResultMapper() {
        var mapper = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Tag(TestRepository.class)
                Mono<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(Mono.just(42));
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        verify(mapper).apply(any());


        var mapperConstructorParameter = repository.objectClass.getConstructors()[0].getParameters()[1];
        assertThat(mapperConstructorParameter.getType()).isEqualTo(R2dbcResultFluxMapper.class);
        var tag = mapperConstructorParameter.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.value()).isEqualTo(new Class<?>[]{compileResult.loadClass("TestRepository")});
    }


    /*
    todo not supported yet

    @Test
    public void testReturnCompletionStageObject() {
        var mapper = Mockito.mock(R2dbcResultFluxMapper.class);
        var repository = compileR2dbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Integer> test();
            }
            """);

        when(mapper.apply(any())).thenReturn(Mono.just(42));
        var result = repository.invoke("test");

        assertThat(result).isEqualTo(42);
        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
        verify(mapper).apply(any());
    }

    @Test
    public void testReturnCompletionStageVoid() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                CompletionStage<Void> test();
            }
            """);

        repository.invoke("test");

        verify(executor.con).createStatement("SELECT count(*) FROM test");
        verify(executor.statement).execute();
    }

    @Test
    public void testReturnCompletionStageUpdateCount() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                CompletionStage<UpdateCount> test();
            }
            """);
        executor.setUpdateCountResult(42);

        var result = repository.<UpdateCount>invoke("test");

        assertThat(result.value()).isEqualTo(42);
        verify(executor.con).createStatement("INSERT INTO test(value) VALUES ('test')");
        verify(executor.statement).execute();
    }
*/

}
