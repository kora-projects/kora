package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository.AllowedMonoResultsRepository;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository.AllowedResultsRepository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcResultsTest {
    private final MockJdbcExecutor executor = new MockJdbcExecutor();
    private final AllowedResultsRepository repository;
    private final AllowedMonoResultsRepository reactiveRepository;
    private final TestContext ctx = new TestContext();

    public JdbcResultsTest() {
        ctx.addContextElement(TypeRef.of(JdbcConnectionFactory.class), executor);
        ctx.addContextElement(TypeRef.of(Executor.class), Runnable::run);
        ctx.addMock(TypeRef.of(JdbcEntity.TestEntityJdbcRowMapperNonFinal.class));
        ctx.addMock(TypeRef.of(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class));
        ctx.addMock(TypeRef.of(JdbcResultSetMapper.class, Void.class));
        ctx.addMock(TypeRef.of(JdbcResultSetMapper.class, Integer.class));
        ctx.addMock(TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, Integer.class)));
        ctx.addMock(TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(Optional.class, Integer.class)));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository.class));
        reactiveRepository = ctx.newInstance(DbTestUtils.compileClass(AllowedMonoResultsRepository.class));
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(executor.resultSet);
    }

    @Test
    void testReturnVoid() throws SQLException {
        repository.returnVoid();
        Mockito.verify(executor.preparedStatement).execute();
        Mockito.verify(executor.preparedStatement).getUpdateCount();
    }

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

    @Test
    void testReturnBatchUpdateCount() throws SQLException {
        when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{42, 43});
        assertThat(repository.returnBatchUpdate(List.of(1, 2))).isEqualTo(new int[]{42, 43});
        verify(executor.preparedStatement).executeBatch();
    }

    @Test
    void testReturnUpdateCount() throws SQLException {
        when(executor.preparedStatement.executeLargeUpdate()).thenReturn(42L);
        assertThat(repository.returnUpdateCount()).isEqualTo(new UpdateCount(42));
        verify(executor.preparedStatement).executeLargeUpdate();
    }

}
