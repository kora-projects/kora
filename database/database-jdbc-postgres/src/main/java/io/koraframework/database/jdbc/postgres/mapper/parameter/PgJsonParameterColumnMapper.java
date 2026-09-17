package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * <b>Русский</b>: Конвертер значения в JSON для колонки типа {@code json} либо {@code jsonb}.
 * <hr>
 * <b>English</b>: Converter of a value into JSON for a {@code json} or {@code jsonb} column.
 */
public class PgJsonParameterColumnMapper<T> implements JdbcParameterColumnMapper<T> {

    private final JsonWriter<T> jsonWriter;
    private final String jsonTypeName;

    public PgJsonParameterColumnMapper(JsonWriter<T> jsonWriter, String jsonTypeName) {
        this.jsonWriter = jsonWriter;
        this.jsonTypeName = jsonTypeName;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable T value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.OTHER);
            return;
        }

        var pgObject = new PGobject();
        pgObject.setType(jsonTypeName);
        pgObject.setValue(jsonWriter.toString(value));
        stmt.setObject(index, pgObject);
    }
}
