package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.internal.core.cql.DefaultColumnDefinitions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory;
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord.TestUnknownType;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord.UnknownTypeField;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CassandraParametersTest extends AbstractCassandraRepositoryTest {

    @Test
    public void oldTest() {
        var ctx = new TestContext();
        ctx.addContextElement(TypeRef.of(CassandraConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Void.class, TypeRef.of(Flux.class, Void.class)));
        ctx.addMock(TypeRef.of(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class));
        ctx.addMock(TypeRef.of(CassandraParameterColumnMapper.class, TestUnknownType.class));
        ctx.addMock(TypeRef.of(CassandraParameterColumnMapper.class, UnknownTypeField.class));
        var repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @Test
    void testNativeParameter() {
        var repository = compileCassandra(List.of(), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
                void test(String value1, int value2);
            }
            """);

        repository.invoke("test", "test", 42);

        verify(executor.telemetry).createContext(any(), eq(new QueryContext("INSERT INTO test(value1, value2) VALUES (:value1, :value2)", "INSERT INTO test(value1, value2) VALUES (?, ?)")));
        verify(executor.telemetryCtx).close(null);
        verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)");
        verify(executor.boundStatementBuilder).setString(0, "test");
        verify(executor.boundStatementBuilder).setInt(1, 42);
        verify(executor.mockSession).execute(any(Statement.class));
    }

    @Test
    void testDtoJavaBeanParameter() {
        @SuppressWarnings("unchecked")
        var columnMapper1 = (CassandraParameterColumnMapper<UnknownTypeField>) mock(CassandraParameterColumnMapper.class);
        var columnMapper2 = mock(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class);
        var repository = compileCassandra(List.of(columnMapper1, columnMapper2), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
                void test(ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean entity);
            }
            """);

        var object = TestEntityJavaBean.defaultJavaBean();
        repository.invoke("test", object);

        verify(executor.boundStatementBuilder).setString(0, object.getField1());
        verify(executor.boundStatementBuilder).setInt(1, object.getField2());
        verify(executor.boundStatementBuilder).setInt(2, object.getField3());
        verify(columnMapper1).apply(any(), eq(3), refEq(object.getUnknownTypeField()));
        verify(columnMapper2).apply(any(), eq(5), refEq(object.getMappedField2()));
    }

    @Test
    void testDtoParameterMapping() {
        @SuppressWarnings("unchecked")
        var mapper = (CassandraParameterColumnMapper<TestUnknownType>) mock(CassandraParameterColumnMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:unknownField)")
                void test(ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord.TestUnknownType unknownField);
            }
            """);

        var object = new TestUnknownType();
        repository.invoke("test", object);

        verify(mapper).apply(any(), eq(0), refEq(object));
    }

    @Test
    public void testNativeBatch() {
        var repository = compileCassandra(List.of(), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
                void test(@Batch java.util.List<String> value1, int value2);
            }
            """);


        var columnDefinition = mock(ColumnDefinition.class);
        when(columnDefinition.getName()).thenReturn(CqlIdentifier.fromCql("test"));
        var columnDefinitions = DefaultColumnDefinitions.valueOf(List.of(
            columnDefinition, columnDefinition
        ));
        var codecRegistry = mock(CodecRegistry.class);
        when(executor.boundStatement.getPreparedStatement()).thenReturn(executor.preparedStatement);
        when(executor.boundStatement.codecRegistry()).thenReturn(codecRegistry);
        when(codecRegistry.codecFor(Mockito.any(), Mockito.any(Class.class))).thenReturn(mock(TypeCodec.class));
        when(executor.preparedStatement.getVariableDefinitions()).thenReturn(columnDefinitions);
        BoundStatementBuilder nextStmt;
        var c = Mockito.mockConstruction(BoundStatementBuilder.class, (mock, context) -> {
            when(mock.build()).thenReturn(executor.boundStatement);
        });
        try (c) {
            repository.invoke("test", List.of("test1", "test2"), 42);
            nextStmt = c.constructed().get(0);
        }
        var order = Mockito.inOrder(executor.boundStatementBuilder, nextStmt);

        order.verify(executor.boundStatementBuilder).setString(0, "test1");
        order.verify(executor.boundStatementBuilder).setInt(1, 42);
        order.verify(executor.boundStatementBuilder).build();


        order.verify(nextStmt).setString(0, "test2");
        order.verify(nextStmt).setInt(1, 42);
        order.verify(nextStmt).build();

        order.verifyNoMoreInteractions();
    }

    @Test
    public void testDtoBatch() {
        var repository = compileCassandra(List.of(
            mock(CassandraParameterColumnMapper.class),
            new CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal()
        ), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:entity.field1, :entity.field2, :entity.field3, :entity.unknownTypeField, :entity.mappedField1, :entity.mappedField2)")
                void test(@Batch java.util.List<ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean> entity);
            }
            """);


        var columnDefinition = mock(ColumnDefinition.class);
        when(columnDefinition.getName()).thenReturn(CqlIdentifier.fromCql("test"));
        var columnDefinitions = DefaultColumnDefinitions.valueOf(List.of(
            columnDefinition, columnDefinition
        ));
        var codecRegistry = mock(CodecRegistry.class);
        when(executor.boundStatement.getPreparedStatement()).thenReturn(executor.preparedStatement);
        when(executor.boundStatement.codecRegistry()).thenReturn(codecRegistry);
        when(codecRegistry.codecFor(Mockito.any(), Mockito.any(Class.class))).thenReturn(mock(TypeCodec.class));
        when(executor.preparedStatement.getVariableDefinitions()).thenReturn(columnDefinitions);
        BoundStatementBuilder nextStmt;
        var c = Mockito.mockConstruction(BoundStatementBuilder.class, (mock, context) -> {
            when(mock.build()).thenReturn(executor.boundStatement);
        });
        var dto1 = TestEntityJavaBean.defaultJavaBean();
        var dto2 = TestEntityJavaBean.defaultJavaBean();
        dto2.setField1("field1_2");

        try (c) {
            repository.invoke("test", List.of(
                dto1,
                dto2
            ));
            nextStmt = c.constructed().get(0);
        }

        var order = Mockito.inOrder(executor.boundStatementBuilder, nextStmt);

        order.verify(executor.boundStatementBuilder).setString(0, "field1");
        order.verify(executor.boundStatementBuilder).setInt(1, 42);
        order.verify(executor.boundStatementBuilder).build();


        order.verify(nextStmt).setString(0, "field1_2");
        order.verify(nextStmt).setInt(1, 42);
        order.verify(nextStmt).build();

        order.verifyNoMoreInteractions();
    }

    @Test
    void testParametersWithSimilarNames() {
        var repository = compileCassandra(List.of(), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                void test(String value, int valueTest);
            }
            """);

        repository.invoke("test", "test", 42);

        Mockito.verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)");
        Mockito.verify(executor.boundStatementBuilder).setString(0, "test");
        Mockito.verify(executor.boundStatementBuilder).setInt(1, 42);
    }

    @Test
    public void testEntityFieldMapping() {
        var repository = compileCassandra(List.of(), """
            public final class StringToJsonbParameterMapper implements CassandraParameterColumnMapper<String> {
                
                @Override
                public void apply(SettableByName<?> stmt, int index, String value) {
                    stmt.set(index, java.util.Map.of("test", value), java.util.Map.class);
                }
            }
            """, """
            public record SomeEntity(long id, @Mapping(StringToJsonbParameterMapper.class) String value) {}
                
            """, """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                void test(SomeEntity entity);
            }
            """);

        repository.invoke("test", newObject("SomeEntity", 42L, "test-value"));

        verify(executor.boundStatementBuilder).setLong(0, 42L);
        verify(executor.boundStatementBuilder).set(1, Map.of("test", "test-value"), Map.class);
    }

    @Test
    public void testNativeParameterWithMapping() {
        var repository = compileCassandra(List.of(), """
            public final class StringToJsonbParameterMapper implements CassandraParameterColumnMapper<String> {
                
                @Override
                public void apply(SettableByName<?> stmt, int index, String value) {
                    stmt.set(index, java.util.Map.of("test", value), java.util.Map.class);
                }
            }
            """, """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(StringToJsonbParameterMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.boundStatementBuilder).setLong(0, 42L);
        verify(executor.boundStatementBuilder).set(1, Map.of("test", "test-value"), Map.class);
    }


    @Test
    public void testUnknownTypeParameter() {
        var mapper = Mockito.mock(CassandraParameterColumnMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, UnknownType value);
            }
            """, """
            public class UnknownType {}
            """);

        repository.invoke("test", 42L, newObject("UnknownType"));

        verify(executor.boundStatementBuilder).setLong(0, 42L);
        verify(mapper).apply(same(executor.boundStatementBuilder), eq(1), any());
    }

    @Test
    public void testUnknownTypeEntityField() {
        var mapper = Mockito.mock(CassandraParameterColumnMapper.class);
        var repository = compileCassandra(List.of(mapper), """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value.f)")
                void test(long id, TestEntity value);
            }
            """, """
            public class UnknownType {}
            """, """
            public record TestEntity(UnknownType f){}
            """);

        repository.invoke("test", 42L, newObject("TestEntity", newObject("UnknownType")));

        verify(executor.boundStatementBuilder).setLong(0, 42L);
        verify(mapper).apply(same(executor.boundStatementBuilder), eq(1), any());
    }

    @Test
    public void testNativeParameterNonFinalMapper() {
        var repository = compileCassandra(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements CassandraParameterColumnMapper<String> {
                @Override
                public void apply(SettableByName<?> stmt, int index, String value) {
                    stmt.set(index, java.util.Map.of("test", value), java.util.Map.class);
                }
            }
            """, """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(TestMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.boundStatementBuilder).setLong(0, 42L);
        verify(executor.boundStatementBuilder).set(1, Map.of("test", "test-value"), Map.class);
    }

    @Test
    public void testMultipleParametersWithSameMapper() {
        var repository = compileCassandra(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements CassandraParameterColumnMapper<String> {
                @Override
                public void apply(SettableByName<?> stmt, int index, String value) {
                    stmt.set(index, java.util.Map.of("test", value), java.util.Map.class);
                }
            }
            """, """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test1(long id, @Mapping(TestMapper.class) String value);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(TestMapper.class) String value);
            }
            """);
    }

    @Test
    public void testMultipleParameterFieldsWithSameMapper() {
        var repository = compileCassandra(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements CassandraParameterColumnMapper<TestRecord> {
                @Override
                public void apply(SettableByName<?> stmt, int index, TestRecord value) {
                    stmt.set(index, java.util.Map.of("test", value.toString()), java.util.Map.class);
                }
            }
            """, """
            @Repository
            public interface TestRepository extends CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value.f1)")
                void test1(long id, TestRecord value);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value.f1)")
                void test2(long id, TestRecord value);
                @Query("INSERT INTO test(id, value1, value2) VALUES (:id, :value1.f1, :value2.f1)")
                void test2(long id, TestRecord value1, TestRecord value2);
            }
            """, """
            public record TestRecord(@Mapping(TestMapper.class) TestRecord f1, @Mapping(TestMapper.class) TestRecord f2){}
            """);
    }
}
