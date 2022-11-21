package ru.tinkoff.kora.database.vertx;

import io.vertx.core.buffer.Buffer;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;
import ru.tinkoff.kora.netty.common.NettyCommonModule;
import ru.tinkoff.kora.vertx.common.VertxCommonModule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VertxDatabaseBaseModule extends NettyCommonModule, VertxCommonModule {
    default <T> VertxRowSetMapper<T> vertxSingleRowSetMapper(VertxRowMapper<T> rowMapper) {
        return VertxRowSetMapper.singleRowSetMapper(rowMapper);
    }

    default <T> VertxRowSetMapper<Optional<T>> vertxOptionalRowSetMapper(VertxRowMapper<T> rowMapper) {
        return VertxRowSetMapper.optionalRowSetMapper(rowMapper);
    }

    default VertxRowMapper<String> stringVertxRowMapper() {
        return row -> row.getString(0);
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
}
