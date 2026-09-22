package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgArrayParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgPrimitiveArrayParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgArrayResultColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgPrimitiveArrayResultColumnMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * <b>Русский</b>: Конвертеры массивов Java в массивы PostgreSQL. Элемент {@code NULL} массива PostgreSQL
 * не может быть представлен в примитивном массиве Java и при чтении приводит к ошибке.
 * <hr>
 * <b>English</b>: Converters of Java arrays into PostgreSQL arrays. A PostgreSQL array {@code NULL} element
 * can't be represented in a Java primitive array and causes an error when read.
 */
public interface PgArrayJdbcMappersModule {

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<boolean[]> primitiveBooleanArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<boolean[]> primitiveBooleanArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(boolean[]::new);
    }

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
    default JdbcParameterColumnMapper<short[]> primitiveShortArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<short[]> primitiveShortArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(short[]::new);
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
    default JdbcParameterColumnMapper<int[]> primitiveIntegerArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<int[]> primitiveIntegerArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(int[]::new);
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
    default JdbcParameterColumnMapper<long[]> primitiveLongArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<long[]> primitiveLongArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(long[]::new);
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
    default JdbcParameterColumnMapper<float[]> primitiveFloatArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<float[]> primitiveFloatArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(float[]::new);
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
    default JdbcParameterColumnMapper<double[]> primitiveDoubleArrayPostgresJdbcParameterColumnMapper() {
        return new PgPrimitiveArrayParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<double[]> primitiveDoubleArrayPostgresJdbcResultColumnMapper() {
        return new PgPrimitiveArrayResultColumnMapper<>(double[]::new);
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
