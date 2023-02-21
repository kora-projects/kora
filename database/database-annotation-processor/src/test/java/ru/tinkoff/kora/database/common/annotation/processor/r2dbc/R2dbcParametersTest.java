package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory;
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.verify;

public class R2dbcParametersTest extends AbstractR2dbcRepositoryTest {

    @Test
    void oldTest() {
        var ctx = new TestContext();
        ctx.addContextElement(TypeRef.of(R2dbcConnectionFactory.class), executor);
        ctx.addContextElement(TypeRef.of(Executor.class), Runnable::run);
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(R2dbcParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapperNonFinal.class));
        var repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @Test
    public void testConnectionParameter() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                void test(Connection connection);
            }
            """);

        repository.invoke("test", executor.con);
    }

    @Test
    void testParametersWithSimilarNames() {
        var repository = compileR2dbc(List.of(), """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                void test(String value, int valueTest);
            }
            """);

        repository.invoke("test", "test", 42);

        verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)");
        verify(executor.statement).bind(0, "test");
        verify(executor.statement).bind(1, 42);
    }


    @Test
    public void testEntityFieldMapping() {
        var repository = compileR2dbc(List.of(), """
            public final class StringToJsonbParameterMapper implements R2dbcParameterColumnMapper<String> {
                
                @Override
                public void apply(Statement stmt, int index, String value) {
                    stmt.bind(index, java.util.Map.of("test", value));
                }
            }
            """, """
            public record SomeEntity(long id, @Mapping(StringToJsonbParameterMapper.class) String value) {}
                
            """, """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                void test(SomeEntity entity);
            }
            """);

        repository.invoke("test", newObject("SomeEntity", 42L, "test-value"));

        verify(executor.statement).bind(0, 42L);
        verify(executor.statement).bind(1, Map.of("test", "test-value"));
    }

    @Test
    public void testNativeParameterWithMapping() {
        var repository = compileR2dbc(List.of(), """
            public final class StringToJsonbParameterMapper implements R2dbcParameterColumnMapper<String> {
                
                @Override
                public void apply(Statement stmt, int index, String value) {
                    stmt.bind(index, java.util.Map.of("test", value));
                }
            }
            """, """
            @Repository
            public interface TestRepository extends R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(StringToJsonbParameterMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.statement).bind(0, 42L);
        verify(executor.statement).bind(1, Map.of("test", "test-value"));
    }
}
