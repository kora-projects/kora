package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;

import java.sql.SQLException;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcParametersTest {
    private final MockJdbcExecutor executor = new MockJdbcExecutor();
    private final AllowedParametersRepository repository;
    private final TestContext ctx = new TestContext();

    public JdbcParametersTest() {
        ctx.addContextElement(TypeRef.of(JdbcConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(JdbcParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(JdbcEntity.TestEntityFieldJdbcParameterColumnMapperNonFinal.class));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(executor.preparedStatement);
    }

    @Test
    void testConnectionParameter() {
        repository.connectionParameter(executor.mockConnection);
    }

    @Test
    void testNativeParameter() throws SQLException {
        repository.nativeParameter("test", 42);

        Mockito.verify(executor.preparedStatement).setString(1, "test");
        Mockito.verify(executor.preparedStatement).setInt(2, 42);
    }

    @Test
    void testDtoParameter() throws SQLException {
        repository.dtoJavaBeanParameter(TestEntityJavaBean.defaultJavaBean());

        Mockito.verify(executor.preparedStatement).setString(1, "field1");
        Mockito.verify(executor.preparedStatement).setInt(2, 42);
    }

    @Test
    void testDtoParameterMapping() throws SQLException {
        repository.dtoRecordParameterMapping(TestEntityRecord.defaultRecord());

        Mockito.verify(executor.preparedStatement).setString(1, "field1");
        Mockito.verify(executor.preparedStatement).setInt(2, 42);
    }

    @Test
    void testNativeBatch() throws SQLException {
        Mockito.when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{10, 10, 10});
        repository.nativeParameterBatch(List.of("test1", "test2"), 42);
        var order = Mockito.inOrder(executor.preparedStatement);

        order.verify(executor.preparedStatement).setString(1, "test1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).setString(1, "test2");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).executeBatch();
        order.verify(executor.preparedStatement).close();

        order.verifyNoMoreInteractions();
    }

    @Test
    void testDtoBatch() throws SQLException {
        Mockito.when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{10, 10, 10});
        repository.mappedBatch(List.of(
            TestEntityJavaBean.defaultJavaBean(),
            TestEntityJavaBean.defaultJavaBean()
        ));
        var order = Mockito.inOrder(executor.preparedStatement);

        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).setInt(3, 43);
        order.verify(executor.preparedStatement).addBatch();
        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).setInt(3, 43);
        order.verify(executor.preparedStatement).addBatch();
        order.verify(executor.preparedStatement).executeBatch();
        order.verify(executor.preparedStatement).close();

        order.verifyNoMoreInteractions();
    }

    @Test
    void testMappedDtoBatch() throws SQLException {
        Mockito.when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{10, 10, 10});
        repository.mappedBatch(List.of(
            TestEntityJavaBean.defaultJavaBean(),
            TestEntityJavaBean.defaultJavaBean()
        ));
        var order = Mockito.inOrder(executor.preparedStatement);

        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).executeBatch();
        order.verify(executor.preparedStatement).close();

        order.verifyNoMoreInteractions();
    }

    @Test
    void parametersWithSimilarNames() throws SQLException {
        repository.parametersWithSimilarNames("test", 42);

        Mockito.verify(executor.mockConnection).prepareStatement("INSERT INTO test(value1, value2) VALUES (?, ?)");
        Mockito.verify(executor.preparedStatement).setString(1, "test");
        Mockito.verify(executor.preparedStatement).setInt(2, 42);
    }
}
