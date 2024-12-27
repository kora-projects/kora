package ru.tinkoff.kora.database.vertx;

import io.vertx.core.buffer.Buffer;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.DataBaseModule;
import ru.tinkoff.kora.database.common.EnumColumnIntMapping;
import ru.tinkoff.kora.database.common.EnumColumnShortMapping;
import ru.tinkoff.kora.database.common.EnumColumnStringMapping;
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxResultColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;
import ru.tinkoff.kora.netty.common.NettyCommonModule;
import ru.tinkoff.kora.vertx.common.VertxCommonModule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public interface VertxDatabaseBaseModule extends NettyCommonModule, VertxCommonModule, DataBaseModule {

    default <T> VertxRowSetMapper<Optional<T>> vertxOptionalRowSetMapper(VertxRowMapper<T> rowMapper) {
        return VertxRowSetMapper.optionalRowSetMapper(rowMapper);
    }

    default VertxRowSetMapper<Void> voidRowSetMapper() {
        return rows -> null;
    }

    default VertxRowMapper<String> stringVertxRowMapper() {
        return row -> row.getString(0);
    }

    default VertxRowMapper<Short> shortVertxRowMapper() {
        return row -> row.getShort(0);
    }

    default VertxRowMapper<Integer> integerVertxRowMapper() {
        return row -> row.getInteger(0);
    }

    default VertxRowMapper<Long> longVertxRowMapper() {
        return row -> row.getLong(0);
    }

    default VertxRowMapper<Double> doubleVertxRowMapper() {
        return row -> row.getDouble(0);
    }

    default VertxRowMapper<Float> floatVertxRowMapper() {
        return row -> row.getFloat(0);
    }

    default VertxRowMapper<Boolean> booleanVertxRowMapper() {
        return row -> row.getBoolean(0);
    }

    default VertxRowMapper<Buffer> bufferVertxRowMapper() {
        return row -> row.getBuffer(0);
    }

    default VertxRowMapper<LocalDate> localDateVertxRowMapper() {
        return row -> row.getLocalDate(0);
    }

    default VertxRowMapper<LocalDateTime> localDateTimeVertxRowMapper() {
        return row -> row.getLocalDateTime(0);
    }

    default VertxRowMapper<BigDecimal> bigDecimalTimeVertxRowMapper() {
        return row -> row.getNumeric(0).bigDecimalValue();
    }

    default VertxRowMapper<BigInteger> bigIntegerTimeVertxRowMapper() {
        return row -> row.getNumeric(0).bigIntegerValue();
    }

    default VertxRowMapper<UUID> uuidTimeVertxRowMapper() {
        return row -> row.getUUID(0);
    }

    // Parameter
    @DefaultComponent
    default <T extends Enum<T> & EnumColumnIntMapping> VertxParameterColumnMapper<T> enumIntVertxParameterColumnMapper() {
        return (o) -> o.valueAsInt();
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnShortMapping> VertxParameterColumnMapper<T> enumShortVertxParameterColumnMapper() {
        return (o) -> o.valueAsShort();
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnStringMapping> VertxParameterColumnMapper<T> enumStringVertxParameterColumnMapper() {
        return (o) -> o.toString();
    }

    // RowColumn
    @DefaultComponent
    default <T extends Enum<T> & EnumColumnIntMapping> VertxResultColumnMapper<T> enumIntCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<Integer, T>();
        for (T e : enums) {
            enumMap.put(e.valueAsInt(), e);
        }

        return (row, index) -> {
            var value = row.getInteger(index);
            if (value == null) {
                return null;
            }
            return enumMap.get(value);
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnShortMapping> VertxResultColumnMapper<T> enumShortCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<Short, T>();
        for (T e : enums) {
            enumMap.put(e.valueAsShort(), e);
        }

        return (row, index) -> {
            var value = row.getShort(index);
            if (value == null) {
                return null;
            }
            return enumMap.get(value);
        };
    }

    @DefaultComponent
    default <T extends Enum<T> & EnumColumnStringMapping> VertxResultColumnMapper<T> enumStringCassandraRowColumnMapper(TypeRef<T> typeRef) {
        final T[] enums = typeRef.getRawType().getEnumConstants();
        var enumMap = new HashMap<String, T>();
        for (T e : enums) {
            enumMap.put(e.toString(), e);
        }

        return (row, index) -> {
            var value = row.getString(index);
            if (value == null) {
                return null;
            }
            return enumMap.get(value);
        };
    }
}
