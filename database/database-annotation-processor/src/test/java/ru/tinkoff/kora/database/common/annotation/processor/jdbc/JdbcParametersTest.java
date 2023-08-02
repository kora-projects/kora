package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.JdbcDatabaseConfig;
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
    public void testAbstractClassRepository() throws SQLException {
        var config = Mockito.mock(JdbcDatabaseConfig.class);
        var repository = compileForArgs(List.of(config, executor), """
            import ru.tinkoff.kora.database.jdbc.JdbcDatabaseConfig;
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                        
                private final JdbcDatabaseConfig config;
                
                public TestRepository(JdbcDatabaseConfig config) {
                    this.config = config;
                }
                       
                @Query("INSERT INTO test(test) VALUES ('test')")
                abstract void testConnection(Connection connection);
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
    public void testEntityFieldMappingByTag() throws SQLException, ClassNotFoundException {
        var mapper = Mockito.mock(JdbcParameterColumnMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            public record SomeEntity(long id, @ru.tinkoff.kora.json.common.annotation.Json String value) {}
                
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                void test(SomeEntity entity);
            }
            """);

        repository.invoke("test", newObject("SomeEntity", 42L, "test-value"));

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(mapper).set(any(), eq(2), eq("test-value"));

        var mapperConstructorParameter = repository.objectClass.getConstructors()[0].getParameters()[1];
        assertThat(mapperConstructorParameter.getType()).isEqualTo(JdbcParameterColumnMapper.class);
        var tag = mapperConstructorParameter.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.value()).isEqualTo(new Class<?>[]{Json.class});
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

    @Test
    public void testUnknownTypeParameter() throws SQLException {
        var mapper = Mockito.mock(JdbcParameterColumnMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, UnknownType value);
            }
            """, """
            public class UnknownType {}
            """);

        repository.invoke("test", 42L, newObject("UnknownType"));

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(mapper).set(same(executor.preparedStatement), eq(2), any());
    }

    @Test
    public void testUnknownTypeEntityField() throws SQLException {
        var mapper = Mockito.mock(JdbcParameterColumnMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value.f)")
                void test(long id, TestEntity value);
            }
            """, """
            public class UnknownType {}
            """, """
            public record TestEntity(UnknownType f){}
            """);

        repository.invoke("test", 42L, newObject("TestEntity", newObject("UnknownType")));

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(mapper).set(same(executor.preparedStatement), eq(2), any());
    }

    @Test
    public void testNativeParameterNonFinalMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements JdbcParameterColumnMapper<String> {
                
                @Override
                public void set(PreparedStatement stmt, int index, String value) throws SQLException {
                    stmt.setObject(index, java.util.Map.of("test", value));
                }
            }
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(TestMapper.class) String value);
            }
            """);

        repository.invoke("test", 42L, "test-value");

        verify(executor.preparedStatement).setLong(1, 42L);
        verify(executor.preparedStatement).setObject(2, Map.of("test", "test-value"));
    }

    @Test
    public void testMultipleParametersWithSameMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements JdbcParameterColumnMapper<String> {
                
                @Override
                public void set(PreparedStatement stmt, int index, String value) throws SQLException {
                    stmt.setObject(index, java.util.Map.of("test", value));
                }
            }
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test1(long id, @Mapping(TestMapper.class) String value);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                void test(long id, @Mapping(TestMapper.class) String value);
            }
            """);
    }

    @Test
    public void testMultipleParameterFieldsWithSameMapper() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestMapper")), """
            public class TestMapper implements JdbcParameterColumnMapper<TestRecord> {
                
                @Override
                public void set(PreparedStatement stmt, int index, TestRecord value) throws SQLException {
                    stmt.setObject(index, java.util.Map.of("test", value.toString()));
                }
            }
            """, """
            @Repository
            public interface TestRepository extends JdbcRepository {
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

    @Test
    public void testParameterMappingByTag() throws ClassNotFoundException, SQLException {
        var mapper = Mockito.mock(JdbcParameterColumnMapper.class);
        var repository = compileJdbc(List.of(mapper), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                void test(@Tag(TestRepository.class) String value);
            }
            """);

        repository.invoke("test", "test-value");

        verify(mapper).set(any(), eq(1), eq("test-value"));

        var mapperConstructorParameter = repository.objectClass.getConstructors()[0].getParameters()[1];
        assertThat(mapperConstructorParameter.getType()).isEqualTo(JdbcParameterColumnMapper.class);
        var tag = mapperConstructorParameter.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.value()).isEqualTo(new Class<?>[]{compileResult.loadClass("TestRepository")});
    }

    @Test
    public void testRecordParameterMapping() throws ClassNotFoundException, SQLException {
        var mapper = Mockito.mock(JdbcParameterColumnMapper.class);
        var repository = compileJdbc(List.of(mapper), """            
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value::jsonb)")
                void test(@Tag(TestRepository.class) TestRecord value);
            }
            """, """
            public record TestRecord(String value){}
            """);

        var mapperConstructorParameter = repository.objectClass.getConstructors()[0].getParameters()[1];
        assertThat(mapperConstructorParameter.getType()).isEqualTo(JdbcParameterColumnMapper.class);
        var tag = mapperConstructorParameter.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.value()).isEqualTo(new Class<?>[]{compileResult.loadClass("TestRepository")});
    }

}
