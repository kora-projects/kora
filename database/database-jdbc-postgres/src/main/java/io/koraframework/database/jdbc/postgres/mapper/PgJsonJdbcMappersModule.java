package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.PgJson;
import io.koraframework.database.jdbc.postgres.annotation.PgJsonb;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgJsonNullableParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgJsonParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgJsonNullableResultColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgJsonResultColumnMapper;
import io.koraframework.json.common.JsonNullable;
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
    default <T> JdbcParameterColumnMapper<T> jsonPostgresJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonParameterColumnMapper<>(jsonWriter, "json");
    }

    @PgJson
    @DefaultComponent
    default <T> JdbcResultColumnMapper<T> jsonPostgresJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonResultColumnMapper<>(jsonReader);
    }

    @PgJson
    @DefaultComponent
    default <T> JdbcParameterColumnMapper<JsonNullable<T>> jsonNullablePostgresJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonNullableParameterColumnMapper<>(jsonWriter, "json");
    }

    @PgJson
    @DefaultComponent
    default <T> JdbcResultColumnMapper<JsonNullable<T>> jsonNullablePostgresJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonNullableResultColumnMapper<>(jsonReader);
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcParameterColumnMapper<T> jsonbPostgresJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonParameterColumnMapper<>(jsonWriter, "jsonb");
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcResultColumnMapper<T> jsonbPostgresJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonResultColumnMapper<>(jsonReader);
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcParameterColumnMapper<JsonNullable<T>> jsonbNullablePostgresJdbcParameterColumnMapper(JsonWriter<T> jsonWriter) {
        return new PgJsonNullableParameterColumnMapper<>(jsonWriter, "jsonb");
    }

    @PgJsonb
    @DefaultComponent
    default <T> JdbcResultColumnMapper<JsonNullable<T>> jsonbNullablePostgresJdbcResultColumnMapper(JsonReader<T> jsonReader) {
        return new PgJsonNullableResultColumnMapper<>(jsonReader);
    }
}
