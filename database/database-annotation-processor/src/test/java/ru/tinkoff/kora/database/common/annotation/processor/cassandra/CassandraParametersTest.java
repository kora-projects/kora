package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.internal.core.cql.DefaultColumnDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord.UnknownTypeField;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord.TestUnknownType;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CassandraParametersTest {
    private final TestContext ctx = new TestContext();
    private final MockCassandraExecutor executor = new MockCassandraExecutor();
    private final AllowedParametersRepository repository;

    public CassandraParametersTest() {
        ctx.addContextElement(TypeRef.of(CassandraConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Void.class, TypeRef.of(Flux.class, Void.class)));
        ctx.addMock(TypeRef.of(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class));
        ctx.addMock(TypeRef.of(CassandraParameterColumnMapper.class, TestUnknownType.class));
        ctx.addMock(TypeRef.of(CassandraParameterColumnMapper.class, UnknownTypeField.class));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @BeforeEach
    void setUp() {
        ctx.resetMocks();
        executor.reset();
    }

    @Test
    void testNativeParameter() {
        repository.nativeParameter("test", 42);

        verify(executor.telemetry).createContext(any(), eq(new QueryContext("INSERT INTO test(value1, value2) VALUES (:value1, :value2)", "INSERT INTO test(value1, value2) VALUES (?, ?)")));
        verify(executor.telemetryCtx).close(null);
        verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)");
        verify(executor.boundStatementBuilder).setString(0, "test");
        verify(executor.boundStatementBuilder).setInt(1, 42);
        verify(executor.mockSession).execute(any(Statement.class));
    }

    @Test
    void testDtoJavaBeanParameter() {
        var object = TestEntityJavaBean.defaultJavaBean();
        repository.dtoJavaBeanParameter(object);

        verify(executor.boundStatementBuilder).setString(0, object.getField1());
        verify(executor.boundStatementBuilder).setInt(1, object.getField2());
        verify(executor.boundStatementBuilder).setInt(2, object.getField3());
        verify(ctx.findInstance(TypeRef.<CassandraParameterColumnMapper<UnknownTypeField>>of(CassandraParameterColumnMapper.class, UnknownTypeField.class))).apply(any(), eq(3), refEq(object.getUnknownTypeField()));
        verify(ctx.findInstance(TypeRef.<CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal>of(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class))).apply(any(), eq(5), refEq(object.getMappedField2()));
    }

    @Test
    void testDtoParameterMapping() {
        var object = new TestUnknownType();
        repository.unknownTypeFieldParameter(object);

        verify(ctx.findInstance(TypeRef.<CassandraParameterColumnMapper<TestUnknownType>>of(CassandraParameterColumnMapper.class, TestUnknownType.class))).apply(any(), eq(0), refEq(object));
    }

    @Test
    void testNativeBatch() {
        var columnDefinition = Mockito.mock(ColumnDefinition.class);
        Mockito.when(columnDefinition.getName()).thenReturn(CqlIdentifier.fromCql("test"));
        var columnDefinitions = DefaultColumnDefinitions.valueOf(List.of(
            columnDefinition, columnDefinition
        ));
        var codecRegistry = Mockito.mock(CodecRegistry.class);
        Mockito.when(executor.boundStatement.getPreparedStatement()).thenReturn(executor.preparedStatement);
        Mockito.when(executor.boundStatement.codecRegistry()).thenReturn(codecRegistry);
        Mockito.when(codecRegistry.codecFor(Mockito.any(), Mockito.any(Class.class))).thenReturn(Mockito.mock(TypeCodec.class));
        Mockito.when(executor.preparedStatement.getVariableDefinitions()).thenReturn(columnDefinitions);
        BoundStatementBuilder nextStmt;
        var c = Mockito.mockConstruction(BoundStatementBuilder.class, (mock, context) -> {
            Mockito.when(mock.build()).thenReturn(executor.boundStatement);
        });
        try (c) {
            repository.nativeParameterBatch(List.of("test1", "test2"), 42);
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
    void testDtoBatch() {
        var columnDefinition = Mockito.mock(ColumnDefinition.class);
        Mockito.when(columnDefinition.getName()).thenReturn(CqlIdentifier.fromCql("test"));
        var columnDefinitions = DefaultColumnDefinitions.valueOf(List.of(
            columnDefinition, columnDefinition
        ));
        var codecRegistry = Mockito.mock(CodecRegistry.class);
        Mockito.when(executor.boundStatement.getPreparedStatement()).thenReturn(executor.preparedStatement);
        Mockito.when(executor.boundStatement.codecRegistry()).thenReturn(codecRegistry);
        Mockito.when(codecRegistry.codecFor(Mockito.any(), Mockito.any(Class.class))).thenReturn(Mockito.mock(TypeCodec.class));
        Mockito.when(executor.preparedStatement.getVariableDefinitions()).thenReturn(columnDefinitions);
        BoundStatementBuilder nextStmt;
        var c = Mockito.mockConstruction(BoundStatementBuilder.class, (mock, context) -> {
            Mockito.when(mock.build()).thenReturn(executor.boundStatement);
        });
        var dto1 = TestEntityJavaBean.defaultJavaBean();
        var dto2 = TestEntityJavaBean.defaultJavaBean();
        dto2.setField1("field1_2");
        try (c) {
            repository.dtoBatch(List.of(
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
        repository.parametersWithSimilarNames("test", 42);

        Mockito.verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)");
        Mockito.verify(executor.boundStatementBuilder).setString(0, "test");
        Mockito.verify(executor.boundStatementBuilder).setInt(1, 42);
    }
}
