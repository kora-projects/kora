package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.json.common.JsonReader;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JsonResultColumnMapper<T> implements JdbcResultColumnMapper<T> {

    private final JsonReader<T> jsonReader;

    public JsonResultColumnMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public @Nullable T apply(ResultSet row, int index) throws SQLException {
        var value = row.getString(index);
        if (row.wasNull()) {
            return null;
        }
        return jsonReader.read(value);
    }
}
