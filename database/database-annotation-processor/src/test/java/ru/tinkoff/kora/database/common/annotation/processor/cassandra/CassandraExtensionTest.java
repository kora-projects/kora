package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity.AllNativeTypesEntity;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CassandraExtensionTest extends AbstractAnnotationProcessorTest {
    @Test
    @SuppressWarnings("unchecked")
    void testEntityRecordListResultSetMapper() throws Exception {
        var cl = TestUtils.testKoraExtension(new TypeRef<?>[]{
                TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, TestEntityJavaBean.class)),
                TypeRef.of(CassandraRowMapper.class, TestEntityJavaBean.class),
                TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, AllNativeTypesEntity.class)),
                TypeRef.of(CassandraRowMapper.class, AllNativeTypesEntity.class),
                TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, String.class)),
            },
            TypeRef.of(CassandraRowColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
            TypeRef.of(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class),
            TypeRef.of(CassandraRowMapper.class, String.class)
        );
    }

    @Test
    void testRowMapper() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule{
              @Root
              default String root(ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper<TestRecord> r) {return "";}
            }
            """, """
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_CassandraRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraRowMapper.class));
    }

    @Test
    public void testListResultSetMapper() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
             
              @Root
              default String root(ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper<java.util.List<TestRecord>> r) {return "";}
            }
            """, """
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        var listMapper = compileResult.loadClass("$TestRecord_ListCassandraResultSetMapper");
        assertThat(listMapper)
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraResultSetMapper.class));

        var columnDefinition = Mockito.mock(ColumnDefinitions.class);
        var rs = Mockito.mock(ResultSet.class);
        @SuppressWarnings("unchecked")
        var mapper = (CassandraResultSetMapper<List<?>>) listMapper.getConstructor().newInstance();

        when(rs.getColumnDefinitions()).thenReturn(columnDefinition);
        when(columnDefinition.firstIndexOf("value")).thenReturn(0);

        var row = Mockito.mock(Row.class);

        when(rs.iterator()).thenReturn(List.of(row, row).iterator());
        var result = mapper.apply(rs);
        assertThat(result).hasSize(2);

        when(columnDefinition.firstIndexOf("value")).thenReturn(0);
        verify(row, times(2)).getInt(0);
    }

    @Test
    public void testSingleResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper<TestRecord> r) {return "";}
            }
            """, """
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_CassandraRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraRowMapper.class));
    }

    private static Predicate<Class<?>> doesImplement(Class<?> anInterface) {
        return aClass -> {
            for (var aClassInterface : aClass.getInterfaces()) {
                if (aClassInterface.equals(anInterface)) {
                    return true;
                }
            }
            return false;
        };
    }
}
