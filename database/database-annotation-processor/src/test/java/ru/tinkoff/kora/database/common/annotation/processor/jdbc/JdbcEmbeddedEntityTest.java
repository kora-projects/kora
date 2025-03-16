package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdbcEmbeddedEntityTest extends AbstractJdbcEntityTest {

    @Test
    public void testSimpleEmbeddedRecord() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (int f1, int f2){}",
            "public record TestRecord (@Embedded EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));
        var rs = mockResultSet(
            of("f1", 10),
            of("f2", 20)
        );

        var row = rowMapper.apply(rs);
        assertThat(row).isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", 10, 20)));
    }

    @Test
    public void testSimpleEmbeddedRecordWithValue() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (int f1, int f2){}",
            "public record TestRecord (@Embedded(\"f1_\") EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));
        var rs = mockResultSet(
            of("f1_f1", 10),
            of("f1_f2", 20)
        );

        var row = rowMapper.apply(rs);
        assertThat(row).isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", 10, 20)));
    }

    @Test
    public void testEmbeddedRecordWithNullableField() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (@Nullable String f1, int f2){}",
            "public record TestRecord (@Embedded EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", "test", 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", (String) null),
            of("f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", null, 20)));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", (Integer) null)
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Result field f1_f2 is not nullable but row f2 has null");
    }

    @Test
    public void testEmbeddedNullableRecordConstructor() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            """
                public record EmbeddedRecord(String f1, int f2) {
                    public EmbeddedRecord(@Nullable String f1, int f2) {
                        this.f1 = f1;
                        this.f2 = f2;
                    }
                }""",
            "public record TestRecord (@Embedded EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", "test", 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", (String) null),
            of("f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", null, 20)));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", (Integer) null)
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Result field f1_f2 is not nullable but row f2 has null");
    }

    @Test
    public void testJavaBeanEmbeddedNullableRecordConstructor() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestBean"));

        var graph = compile(expectedType, List.of(),
            """
                public record EmbeddedRecord(String f1, int f2) {
                    public EmbeddedRecord(@Nullable String f1, int f2) {
                        this.f1 = f1;
                        this.f2 = f2;
                    }
                }""",
            """
                public class TestBean {
                    @Embedded
                    private EmbeddedRecord f1;

                    public EmbeddedRecord getF1() {
                        return f1;
                    }

                    public void setF1(EmbeddedRecord f1) {
                        this.f1 = f1;
                    }
                    
                    @Override
                    public boolean equals(Object o) {
                        if (this == o) return true;
                        if (o == null || getClass() != o.getClass()) return false;
                        TestBean testBean = (TestBean) o;
                        return java.util.Objects.equals(f1, testBean.f1);
                    }

                    @Override
                    public int hashCode() {
                        return java.util.Objects.hash(f1);
                    }
                }"""
        );

        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", 20)
        )))
            .isEqualTo(newJavaBean("TestBean", newObject("EmbeddedRecord", "test", 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", (String) null),
            of("f2", 20)
        )))
            .isEqualTo(newJavaBean("TestBean", newObject("EmbeddedRecord", null, 20)));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", "test"),
            of("f2", (Integer) null)
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Result field f1_f2 is not nullable but row f2 has null");
    }

    @Test
    public void testEmbeddedRecordWithNullableFieldWithValue() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (@Nullable String f1, int f2){}",
            "public record TestRecord (@Embedded(\"f1_\") EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1_f1", "test"),
            of("f1_f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", "test", 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1_f1", (String) null),
            of("f1_f2", 20)
        )))
            .isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", null, 20)));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1_f1", "test"),
            of("f1_f2", (Integer) null)
        )))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Result field f1_f2 is not nullable but row f1_f2 has null");
    }

    @Test
    public void testNullableEmbeddedRecord() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (int f1, int f2){}",
            "public record TestRecord (@Nullable @Embedded EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));


        assertThat(rowMapper.apply(mockResultSet(
            of("f1", 10),
            of("f2", 20)
        ))).isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", 10, 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1", (Integer) null),
            of("f2", (Integer) null)
        ))).isEqualTo(newObject("TestRecord", new Object[]{null}));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", 10),
            of("f2", (Integer) null)
        ))).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", (Integer) null),
            of("f2", 10)
        ))).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1", 10),
            of("f2", (Integer) null)
        ))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testNullableEmbeddedRecordWithValue() throws SQLException {
        var expectedType = ParameterizedTypeName.get(ClassName.get(JdbcRowMapper.class), className("TestRecord"));

        var graph = compile(expectedType, List.of(),
            "public record EmbeddedRecord (int f1, int f2){}",
            "public record TestRecord (@Nullable @Embedded(\"f1_\") EmbeddedRecord f1){}"
        );
        assertThat(draw.getNodes()).hasSize(2);

        var rowMapper = (JdbcRowMapper<?>) graph.get(draw.getNodes().get(0));


        assertThat(rowMapper.apply(mockResultSet(
            of("f1_f1", 10),
            of("f1_f2", 20)
        ))).isEqualTo(newObject("TestRecord", newObject("EmbeddedRecord", 10, 20)));

        assertThat(rowMapper.apply(mockResultSet(
            of("f1_f1", (Integer) null),
            of("f1_f2", (Integer) null)
        ))).isEqualTo(newObject("TestRecord", new Object[]{null}));

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1_f1", 10),
            of("f1_f2", (Integer) null)
        ))).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1_f1", (Integer) null),
            of("f1_f2", 10)
        ))).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> rowMapper.apply(mockResultSet(
            of("f1_f1", 10),
            of("f1_f2", (Integer) null)
        ))).isInstanceOf(NullPointerException.class);
    }
}
