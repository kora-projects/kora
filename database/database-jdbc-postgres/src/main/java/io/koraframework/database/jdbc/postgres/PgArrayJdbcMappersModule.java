package io.koraframework.database.jdbc.postgres;

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
    default JdbcParameterColumnMapper<Boolean[]> booleanArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Boolean[]> booleanArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Boolean[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Short[]> shortArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Short[]> shortArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Short[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Integer[]> integerArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Integer[]> integerArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Integer[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Long[]> longArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Long[]> longArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Long[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Float[]> floatArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Float[]> floatArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Float[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Double[]> doubleArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Double[]> doubleArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(Double[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<BigDecimal[]> bigDecimalArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<BigDecimal[]> bigDecimalArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(BigDecimal[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<String[]> stringArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<String[]> stringArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(String[]::new);
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<UUID[]> uuidArrayJdbcParameterColumnMapper() {
        return new PgArrayParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<UUID[]> uuidArrayJdbcResultColumnMapper() {
        return new PgArrayResultColumnMapper<>(UUID[]::new);
    }
}
