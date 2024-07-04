package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcEntityTest extends AbstractJdbcEntityTest {

    @Test
    public void testFieldRequired() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record TestRecord(Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));

        var rs = mockResultSet(
            of("f1", 10),
            of("f2", 20)
        );
        var row = rowMapper.apply(rs);
        assertThat(row).isEqualTo(newObject("TestRecord", 10, 20));
    }

    @Test
    public void testFieldNullable() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record TestRecord(@Nullable Integer f1, Integer f2){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));
        var rs = mockResultSet(
            of("f1", (Integer) null),
            of("f2", 20)
        );

        var rowWithNull = rowMapper.apply(rs);
        assertThat(rowWithNull).isEqualTo(newObject("TestRecord", null, 20));
    }

    @Test
    public void testFieldNullableOnCanonicalConstructor() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            """
                public record TestRecord(Integer f1, Integer f2) {
                    public TestRecord(@Nullable Integer f1, Integer f2) {
                        this.f1 = java.util.Objects.requireNonNullElse(f1, 10);
                        this.f2 = java.util.Objects.requireNonNull(f2);
                    }
                    public TestRecord() {
                        this(10, 20);
                    }
                }
                """
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));
        var rs = mockResultSet(
            of("f1", (Integer) null),
            of("f2", 20)
        );

        var rowWithNull = rowMapper.apply(rs);
        assertThat(rowWithNull).isEqualTo(newObject("TestRecord", 10, 20));
    }
}
