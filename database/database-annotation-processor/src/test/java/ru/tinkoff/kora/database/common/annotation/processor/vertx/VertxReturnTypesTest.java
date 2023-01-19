package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;

public class VertxReturnTypesTest extends AbstractVertxRepositoryTest {
    @Test
    public void testReturnCompletableFuture() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                CompletionStage<String> select();
            }
            """);
    }

    @Test
    public void testReturnCompletableFutureWithSqlConnection() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                CompletionStage<String> select(SqlConnection connection);
            }
            """);
    }

    @Test
    public void testReturnCompletableFutureWithSqlClient() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                CompletionStage<String> select(SqlClient connection);
            }
            """);
    }

    @Test
    public void testReturnMono() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                Mono<String> select();
            }
            """);
    }

    @Test
    public void testReturnMonoWithSqlConnection() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                Mono<String> select(SqlConnection connection);
            }
            """);
    }

    @Test
    public void testReturnMonoWithSqlClient() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                Mono<String> select(SqlClient connection);
            }
            """);
    }


    @Test
    public void testReturnFlux() {
        var rowSetMapper = Mockito.mock(VertxRowMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                Flux<String> select();
            }
            """);
    }

    @Test
    public void testReturnFluxWithSqlConnection() {
        var rowSetMapper = Mockito.mock(VertxRowMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                Flux<String> select(SqlConnection connection);
            }
            """);
    }

    @Test
    public void testReturnBlocking() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                String select();
            }
            """);
    }

    @Test
    public void testReturnBlockingWithSqlConnection() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                String select(SqlConnection connection);
            }
            """);
    }

    @Test
    public void testReturnBlockingWithSqlClient() {
        var rowSetMapper = Mockito.mock(VertxRowSetMapper.class);
        this.compileVertx(List.of(rowSetMapper), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("select column from table")
                String select(SqlClient connection);
            }
            """);
    }

    @Test
    public void testBatchVoid() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                CompletionStage<Void> select(@Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchUpdateCount() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                CompletionStage<UpdateCount> select(@Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchConnectionVoid() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                CompletionStage<Void> select(SqlClient client, @Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchConnectionUpdateCount() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                CompletionStage<UpdateCount> select(SqlClient client, @Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchMonoVoid() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                Mono<Void> select(@Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchMonoUpdateCount() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                Mono<UpdateCount> select(@Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchMonoConnectionVoid() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                Mono<Void> select(SqlClient client, @Batch java.util.List<String> param);
            }
            """);
    }

    @Test
    public void testBatchMonoConnectionUpdateCount() {
        this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO table(column) VALUES (:param)")
                Mono<UpdateCount> select(SqlClient client, @Batch java.util.List<String> param);
            }
            """);
    }
}
