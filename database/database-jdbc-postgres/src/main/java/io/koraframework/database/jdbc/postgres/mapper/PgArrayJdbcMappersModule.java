package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.*;
import io.koraframework.database.jdbc.mapper.parameter.*;
import io.koraframework.database.jdbc.mapper.result.*;
import io.koraframework.database.jdbc.postgres.annotation.*;
import io.koraframework.database.jdbc.postgres.mapper.parameter.*;
import io.koraframework.database.jdbc.postgres.mapper.result.*;

import java.math.*;
import java.util.*;
import java.util.function.*;

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
    default JdbcParameterColumnMapper<boolean[]> primitiveBooleanArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_BOOLEAN_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<boolean[]> primitiveBooleanArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_BOOLEAN_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Boolean[]> booleanArrayPostgresJdbcParameterColumnMapper() {return Mappers.BOOLEAN_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Boolean[]> booleanArrayPostgresJdbcResultColumnMapper() {return Mappers.BOOLEAN_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<short[]> primitiveShortArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_SHORT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<short[]> primitiveShortArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_SHORT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Short[]> shortArrayPostgresJdbcParameterColumnMapper() {return Mappers.SHORT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Short[]> shortArrayPostgresJdbcResultColumnMapper() {return Mappers.SHORT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<int[]> primitiveIntegerArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_INT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<int[]> primitiveIntegerArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_INT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Integer[]> integerArrayPostgresJdbcParameterColumnMapper() {return Mappers.INT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Integer[]> integerArrayPostgresJdbcResultColumnMapper() {return Mappers.INT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<long[]> primitiveLongArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_LONG_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<long[]> primitiveLongArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_LONG_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Long[]> longArrayPostgresJdbcParameterColumnMapper() {return Mappers.LONG_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Long[]> longArrayPostgresJdbcResultColumnMapper() {return Mappers.LONG_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<float[]> primitiveFloatArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_FLOAT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<float[]> primitiveFloatArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_FLOAT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Float[]> floatArrayPostgresJdbcParameterColumnMapper() {return Mappers.FLOAT_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Float[]> floatArrayPostgresJdbcResultColumnMapper() {return Mappers.FLOAT_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<double[]> primitiveDoubleArrayPostgresJdbcParameterColumnMapper() {return Mappers.PRIMITIVE_DOUBLE_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<double[]> primitiveDoubleArrayPostgresJdbcResultColumnMapper() {return Mappers.PRIMITIVE_DOUBLE_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Double[]> doubleArrayPostgresJdbcParameterColumnMapper() {return Mappers.DOUBLE_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Double[]> doubleArrayPostgresJdbcResultColumnMapper() {return Mappers.DOUBLE_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<BigDecimal[]> bigDecimalArrayPostgresJdbcParameterColumnMapper() {return Mappers.BIG_DECIMAL_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<BigDecimal[]> bigDecimalArrayPostgresJdbcResultColumnMapper() {return Mappers.BIG_DECIMAL_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<String[]> stringArrayPostgresJdbcParameterColumnMapper() {return Mappers.STRING_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<String[]> stringArrayPostgresJdbcResultColumnMapper() {return Mappers.STRING_RESULT;}

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<UUID[]> uuidArrayPostgresJdbcParameterColumnMapper() {return Mappers.UUID_PARAM;}

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<UUID[]> uuidArrayPostgresJdbcResultColumnMapper() {return Mappers.UUID_RESULT;}

    class ArrayFactories {
        public static final class PrimitiveBooleanArrayFactory implements IntFunction<boolean[]> {
            @Override
            public boolean[] apply(int value) {return new boolean[value];}
        }

        public static final class BooleanArrayFactory implements IntFunction<Boolean[]> {
            @Override
            public Boolean[] apply(int value) {return new Boolean[value];}
        }

        public static final class PrimitiveShortArrayFactory implements IntFunction<short[]> {
            @Override
            public short[] apply(int value) {return new short[value];}
        }

        public static final class ShortArrayFactory implements IntFunction<Short[]> {
            @Override
            public Short[] apply(int value) {return new Short[value];}
        }

        public static final class PrimitiveIntArrayFactory implements IntFunction<int[]> {
            @Override
            public int[] apply(int value) {return new int[value];}
        }

        public static final class IntArrayFactory implements IntFunction<Integer[]> {
            @Override
            public Integer[] apply(int value) {return new Integer[value];}
        }

        public static final class PrimitiveLongArrayFactory implements IntFunction<long[]> {
            @Override
            public long[] apply(int value) {return new long[value];}
        }

        public static final class LongArrayFactory implements IntFunction<Long[]> {
            @Override
            public Long[] apply(int value) {return new Long[value];}
        }

        public static final class PrimitiveFloatArrayFactory implements IntFunction<float[]> {
            @Override
            public float[] apply(int value) {return new float[value];}
        }

        public static final class FloatArrayFactory implements IntFunction<Float[]> {
            @Override
            public Float[] apply(int value) {return new Float[value];}
        }

        public static final class PrimitiveDoubleArrayFactory implements IntFunction<double[]> {
            @Override
            public double[] apply(int value) {return new double[value];}
        }

        public static final class DoubleArrayFactory implements IntFunction<Double[]> {
            @Override
            public Double[] apply(int value) {return new Double[value];}
        }

        public static final class BigDecimalArrayFactory implements IntFunction<BigDecimal[]> {
            @Override
            public BigDecimal[] apply(int value) {return new BigDecimal[value];}
        }

        public static final class StringArrayFactory implements IntFunction<String[]> {
            @Override
            public String[] apply(int value) {return new String[value];}
        }

        public static final class UuidArrayFactory implements IntFunction<UUID[]> {
            @Override
            public UUID[] apply(int value) {return new UUID[value];}
        }
    }

    class Mappers {
        public static final PgPrimitiveArrayParameterColumnMapper<boolean[]> PRIMITIVE_BOOLEAN_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("bool");
        public static final PgPrimitiveArrayResultColumnMapper<boolean[]> PRIMITIVE_BOOLEAN_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveBooleanArrayFactory());
        public static final PgArrayParameterColumnMapper<Boolean> BOOLEAN_PARAM = new PgArrayParameterColumnMapper<>("bool");
        public static final PgArrayResultColumnMapper<Boolean> BOOLEAN_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.BooleanArrayFactory());

        public static final PgPrimitiveArrayParameterColumnMapper<short[]> PRIMITIVE_SHORT_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("int2");
        public static final PgPrimitiveArrayResultColumnMapper<short[]> PRIMITIVE_SHORT_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveShortArrayFactory());
        public static final PgArrayParameterColumnMapper<Short> SHORT_PARAM = new PgArrayParameterColumnMapper<>("int2");
        public static final PgArrayResultColumnMapper<Short> SHORT_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.ShortArrayFactory());

        public static final PgPrimitiveArrayParameterColumnMapper<int[]> PRIMITIVE_INT_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("int4");
        public static final PgPrimitiveArrayResultColumnMapper<int[]> PRIMITIVE_INT_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveIntArrayFactory());
        public static final PgArrayParameterColumnMapper<Integer> INT_PARAM = new PgArrayParameterColumnMapper<>("int4");
        public static final PgArrayResultColumnMapper<Integer> INT_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.IntArrayFactory());

        public static final PgPrimitiveArrayParameterColumnMapper<long[]> PRIMITIVE_LONG_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("int8");
        public static final PgPrimitiveArrayResultColumnMapper<long[]> PRIMITIVE_LONG_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveLongArrayFactory());
        public static final PgArrayParameterColumnMapper<Long> LONG_PARAM = new PgArrayParameterColumnMapper<>("int8");
        public static final PgArrayResultColumnMapper<Long> LONG_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.LongArrayFactory());

        public static final PgPrimitiveArrayParameterColumnMapper<float[]> PRIMITIVE_FLOAT_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("float4");
        public static final PgPrimitiveArrayResultColumnMapper<float[]> PRIMITIVE_FLOAT_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveFloatArrayFactory());
        public static final PgArrayParameterColumnMapper<Float> FLOAT_PARAM = new PgArrayParameterColumnMapper<>("float4");
        public static final PgArrayResultColumnMapper<Float> FLOAT_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.FloatArrayFactory());

        public static final PgPrimitiveArrayParameterColumnMapper<double[]> PRIMITIVE_DOUBLE_PARAM = new PgPrimitiveArrayParameterColumnMapper<>("float8");
        public static final PgPrimitiveArrayResultColumnMapper<double[]> PRIMITIVE_DOUBLE_RESULT = new PgPrimitiveArrayResultColumnMapper<>(new ArrayFactories.PrimitiveDoubleArrayFactory());
        public static final PgArrayParameterColumnMapper<Double> DOUBLE_PARAM = new PgArrayParameterColumnMapper<>("float8");
        public static final PgArrayResultColumnMapper<Double> DOUBLE_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.DoubleArrayFactory());

        public static final PgArrayParameterColumnMapper<BigDecimal> BIG_DECIMAL_PARAM = new PgArrayParameterColumnMapper<>("numeric");
        public static final PgArrayResultColumnMapper<BigDecimal> BIG_DECIMAL_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.BigDecimalArrayFactory());

        public static final PgArrayParameterColumnMapper<String> STRING_PARAM = new PgArrayParameterColumnMapper<>("varchar");
        public static final PgArrayResultColumnMapper<String> STRING_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.StringArrayFactory());

        public static final PgArrayParameterColumnMapper<UUID> UUID_PARAM = new PgArrayParameterColumnMapper<>("uuid");
        public static final PgArrayResultColumnMapper<UUID> UUID_RESULT = new PgArrayResultColumnMapper<>(new ArrayFactories.UuidArrayFactory());
    }
}
