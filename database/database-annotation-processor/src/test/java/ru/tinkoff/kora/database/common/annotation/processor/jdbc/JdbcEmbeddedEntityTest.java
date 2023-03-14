package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractExtensionTest;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class JdbcEmbeddedEntityTest extends AbstractExtensionTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.common.annotation.Embedded;
            import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;
            """;
    }

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


    public ResultSet mockResultSet(JdbcColumn<?>... columns) throws SQLException {
        var rs = Mockito.mock(ResultSet.class);
        var wasNulls = new Boolean[columns.length];
        var wasNullCounter = 0;
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            if (column.primitive()) {
                wasNulls[wasNullCounter++] = column.value == null;
            }
            when(rs.next()).thenReturn(true);
            column.mock(rs, i + 1);
        }
        if (wasNulls[0] != null) {
            when(rs.wasNull()).thenReturn(wasNulls[0], Arrays.copyOfRange(wasNulls, 1, wasNullCounter));
        }
        when(rs.next()).thenReturn(false);
        return rs;
    }

    public interface SqlBiFunction<P1, P2, R> {
        R apply(P1 p1, P2 p2) throws SQLException;
    }

    private record JdbcColumn<T>(String column, SqlBiFunction<ResultSet, Integer, T> extractor, T value, boolean primitive) {
        void mock(ResultSet rs, int idx) throws SQLException {
            when(rs.findColumn(column)).thenReturn(idx);
            extractor.apply(doAnswer(invocation -> value).when(rs), idx);
        }
    }

    static <T> JdbcColumn<T> of(String column, SqlBiFunction<ResultSet, Integer, T> extractor, T value) {
        return new JdbcColumn<>(column, extractor, value, false);
    }

    static JdbcColumn<String> of(String column, String value) {
        return new JdbcColumn<>(column, ResultSet::getString, value, false);
    }

    static JdbcColumn<Integer> of(String column, Integer value) {
        return new JdbcColumn<>(column, ResultSet::getInt, value, true);
    }
}
