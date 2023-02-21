package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;

public class JdbcParametersTest extends AbstractJdbcRepositoryTest {
    @Test
    void oldTest() throws SQLException {
        var executor = new MockJdbcExecutor();
        var ctx = new TestContext();
        ctx.addContextElement(TypeRef.of(JdbcConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(JdbcParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(JdbcEntity.TestEntityFieldJdbcParameterColumnMapperNonFinal.class));
        AllowedParametersRepository repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));

        repository.dtoJavaBeanParameter(TestEntityJavaBean.defaultJavaBean());
        verify(executor.preparedStatement).setString(1, "field1");
        verify(executor.preparedStatement).setInt(2, 42);
        executor.reset();

        repository.dtoRecordParameterMapping(TestEntityRecord.defaultRecord());
        verify(executor.preparedStatement).setString(1, "field1");
        verify(executor.preparedStatement).setInt(2, 42);
        executor.reset();

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
        executor.reset();

        Mockito.when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{10, 10, 10});
        repository.mappedBatch(List.of(
            TestEntityJavaBean.defaultJavaBean(),
            TestEntityJavaBean.defaultJavaBean()
        ));
        order = Mockito.inOrder(executor.preparedStatement);
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
        executor.reset();

        Mockito.when(executor.preparedStatement.executeBatch()).thenReturn(new int[]{10, 10, 10});
        repository.mappedBatch(List.of(
            TestEntityJavaBean.defaultJavaBean(),
            TestEntityJavaBean.defaultJavaBean()
        ));
        order = Mockito.inOrder(executor.preparedStatement);
        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).setString(1, "field1");
        order.verify(executor.preparedStatement).setInt(2, 42);
        order.verify(executor.preparedStatement).executeBatch();
        order.verify(executor.preparedStatement).close();
        order.verifyNoMoreInteractions();
        executor.reset();

        repository.parametersWithSimilarNames("test", 42);
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value1, value2) VALUES (?, ?)");
        verify(executor.preparedStatement).setString(1, "test");
        verify(executor.preparedStatement).setInt(2, 42);
        executor.reset();
    }

    @Test
    public void testConnectionParameter() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                       
                @Query("INSERT INTO test(test) VALUES ('test')")
                void testConnection(Connection connection);
            }
            """);

        repository.invoke("testConnection", executor.mockConnection);

        verify(executor.mockConnection).prepareStatement("INSERT INTO test(test) VALUES ('test')");
        verify(executor.preparedStatement).execute();
    }

    @Test
    public void testNativeParameter() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query(\"""
                  INSERT INTO test(...) VALUES (
                    :booleanPrimitive,
                    :booleanBoxed,
                    :integerPrimitive,
                    :integerBoxed,
                    :longPrimitive,
                    :longBoxed,
                    :doublePrimitive,
                    :doubleBoxed,
                    :string,
                    :bigDecimal,
                    :byteArray,
                    :localDateTime,
                    :localDate
                )
                \""")
                void nativeParameters(
                    boolean booleanPrimitive,
                    @Nullable Boolean booleanBoxed,
                    int integerPrimitive,
                    @Nullable Integer integerBoxed,
                    long longPrimitive,
                    @Nullable Long longBoxed,
                    double doublePrimitive,
                    @Nullable Double doubleBoxed,
                    String string,
                    java.math.BigDecimal bigDecimal,
                    byte[] byteArray,
                    java.time.LocalDateTime localDateTime,
                    java.time.LocalDate localDate
                );
            }
            """);

        repository.invoke("nativeParameters", true, false, 1, 2, 3L, 4L, 5d, 6d, "7", new BigDecimal(8), new byte[]{9}, LocalDateTime.now(), LocalDate.now());
    }

    @Test
    public void testEntityFieldMapping() throws SQLException {
        var repository = compileJdbc(List.of(), """
            public final class StringToJsonbParameterMapper implements JdbcParameterColumnMapper<String> {
                
                @Override
                public void set(PreparedStatement stmt, int index, String value) throws SQLException {
                    stmt.setObject(index, java.util.Map.of("test", value));
                }
            }
            """, """
            public record SomeEntity(long id, @Mapping(StringToJsonbParameterMapper.class) String value) {}
                
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                void test(SomeEntity entity);
            }
            """);

        repository.invoke("test", newObject("SomeEntity", 42L, "test-value"));

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(executor.preparedStatement).setObject(2, Map.of("test", "test-value"));
    }

    @Test
    public void testNativeParameterWithMapping() throws SQLException {
        var repository = compileJdbc(List.of(), """
            public final class StringToJsonbParameterMapper implements JdbcParameterColumnMapper<String> {
                
                @Override
                public void set(PreparedStatement stmt, int index, String value) throws SQLException {
                    stmt.setObject(index, java.util.Map.of("test", value));
                }
            }
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(StringToJsonbParameterMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(executor.preparedStatement).setObject(2, Map.of("test", "test-value"));
    }
}
