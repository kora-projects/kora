package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.json.common.JsonReader;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <b>Русский</b>: Конвертер значения колонки типа {@code json} либо {@code jsonb} из JSON.
 * <hr>
 * <b>English</b>: Converter of a {@code json} or {@code jsonb} column value from JSON.
 */
public class PgJsonResultColumnMapper<T> implements JdbcResultColumnMapper<T> {

    private final JsonReader<T> jsonReader;

    public PgJsonResultColumnMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public @Nullable T apply(ResultSet row, int index) throws SQLException {
        var value = row.getString(index);
        if (row.wasNull() || value == null) {
            return null;
        }
        return jsonReader.read(value);
    }
}
