package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory;
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class VertxParametersTest extends AbstractVertxRepositoryTest {
    @Test
    void oldTest() {
        var ctx = new TestContext();
        ctx.addContextElement(TypeRef.of(VertxConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(VertxRowSetMapper.class, Void.class));
        ctx.addMock(TypeRef.of(VertxParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(VertxParameterColumnMapper.class, byte[].class));
        ctx.addMock(TypeRef.of(VertxEntity.TestEntityFieldVertxParameterColumnMapperNonFinal.class));
        var repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @Test
    public void testParametersWithSimilarNames() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                void test(String value, int valueTest);

            }
            """);

        repository.invoke("test", "test", 42);

        Mockito.verify(executor.connection).preparedQuery(eq("INSERT INTO test(value1, value2) VALUES ($1, $2)"));
        Mockito.verify(executor.query).execute(ArgumentMatchers.argThat(argument -> Tuple.of("test", 42).deepToString().equals(argument.deepToString())), any());
    }

    @Test
    public void testConnectionParameter() {
        var repository = compileVertx(List.of(), """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                void test(SqlConnection connection);
            }
            """);

        repository.invoke("test", executor.connection);

        verify(executor.query).execute(tupleMatcher(Tuple.tuple()), any());
    }

    @Test
    public void testEntityFieldMapping() {
        var repository = compileVertx(List.of(), """
            public final class StringToJsonbParameterMapper implements VertxParameterColumnMapper<String> {
                
                @Override
                public Object apply(String value) {
                    return java.util.Map.of("test", value);
                }
            }
            """, """
            public record SomeEntity(long id, @Mapping(StringToJsonbParameterMapper.class) String value) {}
                
            """, """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                void test(SomeEntity entity);
            }
            """);

        repository.invoke("test", newObject("SomeEntity", 42L, "test-value"));

        verify(executor.query).execute(tupleMatcher(Tuple.of(42L, Map.of("test", "test-value"))), any());
    }

    @Test
    public void testNativeParameterWithMapping() {
        var repository = compileVertx(List.of(), """
            public final class StringToJsonbParameterMapper implements VertxParameterColumnMapper<String> {
                
                @Override
                public Object apply(String value) {
                    return java.util.Map.of("test", value);
                }
            }
            """, """
            @Repository
            public interface TestRepository extends VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(StringToJsonbParameterMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.query).execute(tupleMatcher(Tuple.of(42L, Map.of("test", "test-value"))), any());
    }
}
