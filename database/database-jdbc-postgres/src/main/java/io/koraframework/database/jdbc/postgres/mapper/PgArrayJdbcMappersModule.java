package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgArrayParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgArrayResultColumnMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * <b>Русский</b>: Конвертеры массивов Java в массивы PostgreSQL. Поддержаны только боксированные типы
 * элемента: примитивный массив не может представить элемент {@code NULL}, который PostgreSQL в массиве допускает.
 * <hr>
 * <b>English</b>: Converters of Java arrays into PostgreSQL arrays. Only boxed element types are supported: a primitive
 * array can't represent a {@code NULL} element, which PostgreSQL permits in an array.
 */
public interface PgArrayJdbcMappersModule {

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Boolean[]> booleanArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Boolean[]> booleanArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Boolean[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Short[]> shortArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Short[]> shortArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Short[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Integer[]> integerArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Integer[]> integerArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Integer[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Long[]> longArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Long[]> longArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Long[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Float[]> floatArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Float[]> floatArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Float[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Double[]> doubleArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Double[]> doubleArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Double[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<BigDecimal[]> bigDecimalArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<BigDecimal[]> bigDecimalArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(BigDecimal[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<String[]> stringArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<String[]> stringArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(String[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<UUID[]> uuidArrayPostgresJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<UUID[]> uuidArrayPostgresJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(UUID[]::new);
    }
}
