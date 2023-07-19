package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.GettableByName;
import com.datastax.oss.driver.api.core.data.SettableByName;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CassandraEntity {
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
        LocalDateTime localDateTime,
        LocalDate localDate
    ) {}

    public static void func1(
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
        LocalDateTime localDateTime,
        LocalDate localDate
    ) {}

    public static final class TestEntityFieldCassandraResultColumnMapper implements CassandraRowColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public TestEntityRecord.MappedField1 apply(GettableByName row, int column) {
            return null;
        }
    }

    public static class TestEntityFieldCassandraResultColumnMapperNonFinal implements CassandraRowColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public TestEntityRecord.MappedField2 apply(GettableByName row, int column) {
            return null;
        }
    }

    public static final class TestEntityFieldCassandraParameterColumnMapper implements CassandraParameterColumnMapper<TestEntityRecord.MappedField1> {
        @Override
        public void apply(SettableByName<?> stmt, int index, TestEntityRecord.MappedField1 value) {

        }
    }

    public static class TestEntityFieldCassandraParameterColumnMapperNonFinal implements CassandraParameterColumnMapper<TestEntityRecord.MappedField2> {
        @Override
        public void apply(SettableByName<?> stmt, int index, TestEntityRecord.MappedField2 value) {

        }
    }

    public static final class TestEntityCassandraRowMapper implements CassandraRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(Row row) {
            return null;
        }
    }

    public static class TestEntityCassandraRowMapperNonFinal implements CassandraRowMapper<TestEntityRecord> {
        @Override
        public TestEntityRecord apply(Row row) {
            return null;
        }
    }

    public static final class ListTestEntityCassandraResultSetMapper implements CassandraReactiveResultSetMapper<List<TestEntityRecord>, Mono<List<TestEntityRecord>>> {
        @Override
        public Mono<List<TestEntityRecord>> apply(ReactiveResultSet rows) {
            return null;
        }
    }
}
