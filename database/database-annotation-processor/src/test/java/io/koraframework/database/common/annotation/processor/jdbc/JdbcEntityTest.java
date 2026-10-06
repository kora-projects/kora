package io.koraframework.database.common.annotation.processor.jdbc;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import io.koraframework.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcEntityTest extends AbstractJdbcEntityTest {

    @Test
    public void testAnnotatedRecordRowMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@io.koraframework.database.jdbc.annotation.EntityJdbc public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcRowMapper.class);
    }

    @Test
    public void testAnnotatedRecordResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@io.koraframework.database.jdbc.annotation.EntityJdbc public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcResultSetMapper.class);
    }

    @Test
    public void testAnnotatedRecordListResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), ParameterizedTypeName.get(ClassName.get(List.class), className("TestRecord")));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@io.koraframework.database.jdbc.annotation.EntityJdbc public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcResultSetMapper.class);
    }

    @Test
    public void testStaticColumnMapperFieldIsNotQualifiedWithThis() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()), """
            public final class TagsMapper implements io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper<String> {
                @Override
                public String apply(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
                    return rs.getString(index);
                }
            }
            """, """
            @io.koraframework.database.jdbc.annotation.EntityJdbc
            public record TestRecord(long id, @io.koraframework.common.annotation.Mapping(TagsMapper.class) String tags) {}
            """);
        compileResult.assertSuccess();

        // javac -Xlint:static warns on `this.staticField`, which fails builds with -Werror
        var generated = Path.of("build", "in-test-generated", "sources").resolve(testPackage().replace('.', '/'));
        for (var name : List.of("$TestRecord_JdbcRowMapper", "$TestRecord_JdbcResultSetMapper", "$TestRecord_ListJdbcResultSetMapper")) {
            var source = Files.readString(generated.resolve(name + ".java"));
            assertThat(source).contains("private static final TagsMapper _tagsMapper").doesNotContain("this._tagsMapper");
        }
    }
}
