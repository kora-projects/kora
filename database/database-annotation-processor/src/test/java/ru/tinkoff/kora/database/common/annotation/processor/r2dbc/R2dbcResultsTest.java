package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.repository.AllowedResultsRepository;
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;

import java.util.concurrent.Executor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class R2dbcResultsTest {
    private final MockR2dbcExecutor executor = new MockR2dbcExecutor();
    private final AllowedResultsRepository repository;
    private final TestContext ctx = new TestContext();

    public R2dbcResultsTest() {
        ctx.addContextElement(TypeRef.of(R2dbcConnectionFactory.class), executor);
        ctx.addContextElement(TypeRef.of(Executor.class), Runnable::run);
        ctx.addMock(TypeRef.of(R2dbcEntity.TestEntityR2dbcRowMapperNonFinal.class));
        ctx.addMock(TypeRef.of(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapperNonFinal.class));
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper.class, TestEntityRecord.class, TypeRef.of(Mono.class, TestEntityRecord.class)));
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper.class, TestEntityRecord.class, TypeRef.of(Flux.class, TestEntityRecord.class)));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository.class));
    }

    @BeforeEach
    void setUp() {
        executor.reset();
    }

    @Test
    void testReturnVoid() {
        repository.returnVoid();
//        Mockito.verify(executor.preparedStatement).execute();
//        Mockito.verify(executor.preparedStatement).getUpdateCount();
    }
/*

    @Test
    void testReturnPrimitive() throws SQLException {
        var rowMapper = ctx.findInstance(TypeRef.of(JdbcResultSetMapper.class, Integer.class));
        when(rowMapper.apply(any())).thenReturn(42);
        assertThat(repository.returnPrimitive()).isEqualTo(42);
        verify(rowMapper).apply(any());
    }

    @Test
    void testReturnObject() throws SQLException {
        var rowMapper = ctx.findInstance(TypeRef.of(JdbcResultSetMapper.class, Integer.class));
        reset(rowMapper);
        when(rowMapper.apply(any())).thenReturn(42);
        assertThat(repository.returnObject()).isEqualTo(42);
        verify(rowMapper).apply(any());
    }

    @Test
    void testReturnNullableObject() throws SQLException {
        var rowMapper = ctx.findInstance(TypeRef.of(JdbcResultSetMapper.class, Integer.class));
        reset(rowMapper);
        when(rowMapper.apply(any())).thenReturn(42);
        assertThat(repository.returnNullableObject()).isEqualTo(42);
        verify(rowMapper).apply(any());

        reset(rowMapper);
        when(rowMapper.apply(any())).thenReturn(null);
        assertThat(repository.returnNullableObject()).isNull();
        verify(rowMapper).apply(any());
    }

    @Test
    void testReturnObjectWithRowMapper() throws SQLException {
        var rowMapper = ctx.findInstance(TypeRef.of(JdbcEntity.TestEntityJdbcRowMapperNonFinal.class));
        reset(rowMapper);
        doReturn(true, false).when(executor.resultSet).next();
        when(rowMapper.apply(any())).thenReturn(null);
        assertThat(repository.returnObjectWithRowMapperNonFinal()).isEqualTo(null);
        verify(rowMapper).apply(any());
    }
*/
}
