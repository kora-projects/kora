package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.PgJson;
import io.koraframework.database.jdbc.postgres.annotation.PgJsonb;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgJsonParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgJsonResultColumnMapper;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;

/**
 * <b>Русский</b>: Конвертеры значения в колонку типа {@code json} либо {@code jsonb} через JSON.
 * <hr>
 * <b>English</b>: Converters of a value into a {@code json} or {@code jsonb} column via JSON.
 *
 * @see PgJson
 * @see PgJsonb
 */
public interface PgJsonJdbcMappersModule {

    @PgJson
    @DefaultComponent
    default <T> JdbcParameterColumnMapper<T> jsonJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonParameterColumnMapper<>(jsonWriter, "json");
    }

    @PgJson
    @DefaultComponent
    default <T> JdbcResultColumnMapper<T> jsonJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonResultColumnMapper<>(jsonReader);
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcParameterColumnMapper<T> jsonbJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonParameterColumnMapper<>(jsonWriter, "jsonb");
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcResultColumnMapper<T> jsonbJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonResultColumnMapper<>(jsonReader);
    }
}
