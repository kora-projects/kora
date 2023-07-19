package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class R2dbcEntity {
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
        byte[] byteArray,
        LocalDateTime localDateTime,
        LocalDate localDate
    ) {}

    public static final class TestEntityFieldR2dbcResultColumnMapper implements R2dbcResultColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public TestEntityRecord.MappedField1 apply(Row row, String label) {
            return null;
        }
    }

    public static class TestEntityFieldR2dbcResultColumnMapperNonFinal implements R2dbcResultColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public TestEntityRecord.MappedField2 apply(Row row, String label) {
            return null;
        }
    }

    public static final class TestEntityR2dbcRowMapper implements R2dbcRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(Row row) {
            return null;
        }
    }

    public static class TestEntityR2dbcRowMapperNonFinal implements R2dbcRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(Row row) {
            return null;
        }
    }

    public static final class TestEntityFieldR2dbcParameterColumnMapper implements R2dbcParameterColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public void apply(Statement stmt, int index, TestEntityRecord.MappedField1 value) {

        }
    }

    public static class TestEntityFieldR2dbcParameterColumnMapperNonFinal implements R2dbcParameterColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public void apply(Statement stmt, int index, TestEntityRecord.MappedField2 value) {

        }
    }
}
