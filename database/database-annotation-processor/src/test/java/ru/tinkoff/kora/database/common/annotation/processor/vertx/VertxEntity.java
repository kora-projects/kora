package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.sqlclient.Row;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxResultColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class VertxEntity {
    public record AllNativeTypesEntity(
        boolean booleanPrimitive,
        @Nullable Boolean booleanBoxed,
        int integerPrimitive,
        @Nullable Integer integerBoxed,
        long longPrimitive,
        @Nullable Long longBoxed,
        double doublePrimitive,
        @Nullable Double doubleBoxed,
        String string,
        BigDecimal bigDecimal,
        Buffer byteArray,
        LocalDateTime localDateTime,
        LocalDate localDate
    ) {}

    public static final class TestEntityFieldVertxResultColumnMapper implements VertxResultColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public TestEntityRecord.MappedField1 apply(Row row, int index) {
            return null;
        }
    }

    public static class TestEntityFieldVertxResultColumnMapperNonFinal implements VertxResultColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public TestEntityRecord.MappedField2 apply(Row row, int index) {
            return null;
        }
    }

    public static final class TestEntityVertxRowMapper implements VertxRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(io.vertx.sqlclient.Row row) {
            return null;
        }
    }

    public static class TestEntityVertxRowMapperNonFinal implements VertxRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(Row row) {
            return null;
        }
    }

    public static final class TestEntityFieldVertxParameterColumnMapper implements VertxParameterColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public Object apply(TestEntityRecord.MappedField1 o) {
            return null;
        }
    }

    public static class TestEntityFieldVertxParameterColumnMapperNonFinal implements VertxParameterColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public Object apply(TestEntityRecord.MappedField2 o) {
            return null;
        }
    }
}
