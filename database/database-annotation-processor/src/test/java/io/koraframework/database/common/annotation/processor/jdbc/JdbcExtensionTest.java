package io.koraframework.database.common.annotation.processor.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.Tag;
import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import io.koraframework.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.sql.ResultSet;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class JdbcExtensionTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
            """
                import io.koraframework.database.jdbc.*;
                import io.koraframework.database.jdbc.mapper.result.*;
                import io.koraframework.database.jdbc.mapper.parameter.*;
                import io.koraframework.common.Mapping;
                import java.sql.*;
                """;
    }

    @Test
    public void testRowMapperWithTags() {
        compile(List.of(new KoraAppProcessor(), new RepositoryAnnotationProcessor(), new JdbcEntityAnnotationProcessor()),
            """
                @KoraApp
                public interface Application extends JdbcDatabaseModule {
                
                    @Root
                    default String testRowMapper(JdbcResultSetMapper<TestRow> tm) {
                        return "";
                    }
                
                    @Tag(String.class)
                    default JdbcResultColumnMapper<String> taggedMapper() {
                        return ResultSet::getString;
                    }
                }
                """,
            """
                @EntityJdbc record TestRow(String f1, String f2, @Tag(String.class) String f3, @Mapping(TestRowResultColumnMapper.class) String f4) { }
                """,
            """
                public final class TestRowResultColumnMapper implements JdbcResultColumnMapper<String> {
                    @Override
                    public String apply(ResultSet row, int index) throws SQLException {
                        return row.getString(index);
                    }
                }
                """
        );

        compileResult.assertSuccess();
        var graph = loadGraphDraw("Application");
        Assertions.assertThat(graph.getNodes()).hasSize(3);

        var mapper = compileResult.loadClass("$TestRow_JdbcRowMapper");
        var constructor = mapper.getConstructors()[0];
        Assertions.assertThat(constructor.getParameters()).hasSize(1);

        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()).hasSize(1);
        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()[0]).isInstanceOf(Tag.class);
    }

    @Test
    public void testRowMapperWithTaggedField() {
        compile(List.of(new KoraAppProcessor(), new JdbcEntityAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
                @io.koraframework.common.Tag(TestRecord.class)
                default io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper<String> taggedColumnMapper() {
                    return java.sql.ResultSet::getString;
                }
            
              @Root
              default String root(io.koraframework.database.jdbc.mapper.result.JdbcRowMapper<TestRecord> r) {return "";}
            }
            """, """
            @EntityJdbc public record TestRecord(@io.koraframework.common.Tag(TestRecord.class) String value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_JdbcRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(JdbcRowMapper.class));
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
