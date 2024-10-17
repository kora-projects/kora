package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcEntityTest extends AbstractJdbcEntityTest {

    @Test
    public void testAnnotatedRecordRowMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@ru.tinkoff.kora.database.jdbc.JdbcEntity public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcRowMapper.class);
    }

    @Test
    public void testAnnotatedRecordResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@ru.tinkoff.kora.database.jdbc.JdbcEntity public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcResultSetMapper.class);
    }

    @Test
    public void testAnnotatedRecordListResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), ParameterizedTypeName.get(ClassName.get(List.class), className("TestRecord")));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "@ru.tinkoff.kora.database.jdbc.JdbcEntity public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcResultSetMapper.class);
    }

    @Test
    public void testNonAnnotatedRecordRowMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);
        assertThat(compileResult.warnings()).hasSize(3);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcRowMapper.class);
    }

    @Test
    public void testNonAnnotatedRecordResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(3);
        assertThat(compileResult.warnings()).hasSize(3);

        assertThat(graph.get(draw.getNodes().get(1))).isInstanceOf(JdbcResultSetMapper.class);
    }

    @Test
    public void testNonAnnotatedRecorListResultSetMapper() {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcResultSetMapper.class), ParameterizedTypeName.get(ClassName.get(List.class), className("TestRecord")));

        var graph = compile(expectedType, List.of(), List.of(new JdbcEntityAnnotationProcessor()),
            "public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);
        assertThat(compileResult.warnings()).hasSize(3);

        assertThat(graph.get(draw.getNodes().get(0))).isInstanceOf(JdbcResultSetMapper.class);
    }
}
