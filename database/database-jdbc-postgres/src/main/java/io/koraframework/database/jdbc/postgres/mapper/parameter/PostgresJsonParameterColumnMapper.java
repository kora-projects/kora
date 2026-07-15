package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class PostgresJsonParameterColumnMapper<T> implements JdbcParameterColumnMapper<T> {

    static final String JSON_TYPE = "jsonb";

    private final JsonWriter<T> jsonWriter;

    public PostgresJsonParameterColumnMapper(JsonWriter<T> jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable T value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.OTHER);
            return;
        }
        var pgObject = new PGobject();
        pgObject.setType(JSON_TYPE);
        pgObject.setValue(jsonWriter.toString(value));
        stmt.setObject(index, pgObject);
    }
}
