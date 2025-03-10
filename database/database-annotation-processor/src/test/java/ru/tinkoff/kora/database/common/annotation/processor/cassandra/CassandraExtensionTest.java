package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraEntityAnnotationProcessor;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CassandraExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.cassandra.mapper.result.*;
            """;
    }

    @Test
    void testRowMapper() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule{
              @Root
              default String root(CassandraRowMapper<TestRecord> r) {return "";}
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
    void testEntityRowMapper() {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule{
              @Root
              default String root(CassandraRowMapper<TestRecord> r) {return "";}
            }
            """, """
            @ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra
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
              default String root(CassandraResultSetMapper<java.util.List<TestRecord>> r) {return "";}
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
    public void testEntityListResultSetMapper() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
            
              @Root
              default String root(CassandraResultSetMapper<java.util.List<TestRecord>> r) {return "";}
            }
            """, """
            @ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra
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
              default String root(CassandraResultSetMapper<TestRecord> r) {return "";}
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
    public void testEntitySingleResultSetMapper() {
        compile(List.of(new KoraAppProcessor(), new CassandraEntityAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraResultSetMapper<TestRecord> r) {return "";}
            }
            """, """
            @ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_CassandraResultSetMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraResultSetMapper.class));
    }

    @Test
    public void testSingleAsyncResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraAsyncResultSetMapper<TestRecord> r) {return "";}
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
    public void testListAsyncResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraAsyncResultSetMapper<java.util.List<TestRecord>> r) {return "";}
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
    public void testSingleReactiveResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            import reactor.core.publisher.Mono;
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraReactiveResultSetMapper<TestRecord, Mono<TestRecord>> r) {return "";}
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
    public void testVoidMono() {
        compile(List.of(new KoraAppProcessor()), """
            import reactor.core.publisher.Mono;
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraReactiveResultSetMapper<Void, Mono<Void>> r) {return "";}
            }
            """, """
            public record TestRecord(int value) {}
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testListReactiveResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            import java.util.List;
            import reactor.core.publisher.Mono;
            
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraReactiveResultSetMapper<List<TestRecord>, Mono<List<TestRecord>>> r) {return "";}
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
    public void testFluxReactiveResultSetMapper() {
        compile(List.of(new KoraAppProcessor()), """
            import reactor.core.publisher.Flux;
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp extends ru.tinkoff.kora.database.cassandra.CassandraModule {
              @Root
              default String root(CassandraReactiveResultSetMapper<TestRecord, Flux<TestRecord>> r) {return "";}
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
