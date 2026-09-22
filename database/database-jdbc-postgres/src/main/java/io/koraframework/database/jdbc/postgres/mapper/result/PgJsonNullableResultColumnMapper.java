package io.koraframework.database.jdbc.postgres.mapper.result;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.json.common.JsonModule;
import io.koraframework.json.common.JsonNullable;
import io.koraframework.json.common.JsonReader;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <b>Русский</b>: Конвертер колонки типа {@code json} либо {@code jsonb} в {@link JsonNullable}.
 * SQL {@code NULL} читается как неопределённое значение, JSON {@code null} — как определённое пустое значение.
 * <hr>
 * <b>English</b>: Converter of a {@code json} or {@code jsonb} column into {@link JsonNullable}.
 * SQL {@code NULL} is read as an undefined value; JSON {@code null} is read as a defined null value.
 */
public final class PgJsonNullableResultColumnMapper<T> implements JdbcResultColumnMapper<JsonNullable<T>> {

    private final JsonReader<T> jsonReader;

    public PgJsonNullableResultColumnMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public JsonNullable<T> apply(ResultSet row, int index) throws SQLException {
        var value = row.getString(index);
        if (row.wasNull() || value == null) {
            return JsonNullable.undefined();
        }

        try (var parser = JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), value)) {
            if (parser.nextToken() == JsonToken.VALUE_NULL) {
                return JsonNullable.nullValue();
            }
        }
        return JsonNullable.ofNullable(jsonReader.read(value));
    }
}
