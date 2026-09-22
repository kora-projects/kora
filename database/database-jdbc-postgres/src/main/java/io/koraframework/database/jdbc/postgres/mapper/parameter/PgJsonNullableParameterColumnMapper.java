package io.koraframework.database.jdbc.postgres.mapper.parameter;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.json.common.JsonNullable;
import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * <b>Русский</b>: Конвертер {@link JsonNullable} в колонку типа {@code json} либо {@code jsonb}.
 * Неопределённое значение записывается как SQL {@code NULL}, определённое пустое значение — как JSON {@code null}.
 * <hr>
 * <b>English</b>: Converter of {@link JsonNullable} into a {@code json} or {@code jsonb} column.
 * An undefined value is written as SQL {@code NULL}; a defined null value is written as JSON {@code null}.
 */
public final class PgJsonNullableParameterColumnMapper<T> implements JdbcParameterColumnMapper<JsonNullable<T>> {

    private final JsonWriter<T> jsonWriter;
    private final String jsonTypeName;

    public PgJsonNullableParameterColumnMapper(JsonWriter<T> jsonWriter, String jsonTypeName) {
        this.jsonWriter = jsonWriter;
        this.jsonTypeName = jsonTypeName;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable JsonNullable<T> value) throws SQLException {
        if (value == null || !value.isDefined()) {
            stmt.setNull(index, Types.OTHER);
            return;
        }

        var pgObject = new PGobject();
        pgObject.setType(jsonTypeName);
        pgObject.setValue(value.isNull() ? "null" : jsonWriter.toString(value.value()));
        stmt.setObject(index, pgObject);
    }
}
