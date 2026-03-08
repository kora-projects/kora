package io.koraframework.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.database.annotation.processor.cassandra.CassandraEntityAnnotationProcessor;
import io.koraframework.database.cassandra.mapper.result.CassandraResultSetMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraRowMapper;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CassandraExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.database.cassandra.mapper.result.*;
            """;
    }

    @Test
    void testEntityRowMapper() {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp extends io.koraframework.database.cassandra.CassandraModule{
              @Root
              default String root(CassandraRowMapper<TestRecord> r) {return "";}
            }
            """, """
            @io.koraframework.database.cassandra.annotation.EntityCassandra
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_CassandraRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraRowMapper.class));
    }

    @Test
    public void testEntityListResultSetMapper() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp extends io.koraframework.database.cassandra.CassandraModule {
            
              @Root
              default String root(CassandraResultSetMapper<java.util.List<TestRecord>> r) {return "";}
            }
            """, """
            @io.koraframework.database.cassandra.annotation.EntityCassandra
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
    public void testEntitySingleResultSetMapper() {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp extends io.koraframework.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraResultSetMapper<TestRecord> r) {return "";}
            }
            """, """
            @io.koraframework.database.cassandra.annotation.EntityCassandra
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_CassandraResultSetMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraResultSetMapper.class));
    }

    @Test
    public void testListAsyncResultSetMapper() {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp extends io.koraframework.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraAsyncResultSetMapper<java.util.List<TestRecord>> r) {return "";}
            }
            """, """
            @io.koraframework.database.cassandra.annotation.EntityCassandra
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
