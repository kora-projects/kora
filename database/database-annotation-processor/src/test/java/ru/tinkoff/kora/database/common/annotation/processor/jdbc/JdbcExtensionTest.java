package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

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
                import ru.tinkoff.kora.database.jdbc.*;
                import ru.tinkoff.kora.database.jdbc.mapper.result.*;
                import ru.tinkoff.kora.database.jdbc.mapper.parameter.*;
                import ru.tinkoff.kora.common.Mapping;
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
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
                @ru.tinkoff.kora.common.Tag(TestRecord.class)
                default ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper<String> taggedColumnMapper() {
                    return java.sql.ResultSet::getString;
                }
            
              @Root
              default String root(ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper<TestRecord> r) {return "";}
            }
            """, """
            @EntityJdbc public record TestRecord(@ru.tinkoff.kora.common.Tag(TestRecord.class) String value) {}
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
