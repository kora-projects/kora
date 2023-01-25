package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class VertxParametersOrderTest extends AbstractVertxRepositoryTest {
    @Test
    public void testRepeatedParameters() {
        var repository = this.compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO TABLE test(value1, value2, value3) VALUES (:value2, :value1, :value2)")
                CompletionStage<Void> insert(String value1, String value2);
            }
            """);

        repository.invoke("insert", "value1", "value2");

        verify(executor.connection).preparedQuery(eq("INSERT INTO TABLE test(value1, value2, value3) VALUES ($2, $1, $2)"));
        verify(executor.query).execute(tupleMatcher(Tuple.of("value1", "value2")), any());
    }
}
