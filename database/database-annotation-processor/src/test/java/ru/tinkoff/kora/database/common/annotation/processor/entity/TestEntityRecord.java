package ru.tinkoff.kora.database.common.annotation.processor.entity;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.NamingStrategy;
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.JdbcEntity;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.R2dbcEntity;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.VertxEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@NamingStrategy(SnakeCaseNameConverter.class)
public record TestEntityRecord(
    String field1,
    int field2,
    @Nullable
    Integer field3,
    UnknownTypeField unknownTypeField,
    @Mapping(JdbcEntity.TestEntityFieldJdbcResultColumnMapper.class)
    @Mapping(JdbcEntity.TestEntityFieldJdbcParameterColumnMapper.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraResultColumnMapper.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraParameterColumnMapper.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcResultColumnMapper.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapper.class)
    @Mapping(VertxEntity.TestEntityFieldVertxResultColumnMapper.class)
    @Mapping(VertxEntity.TestEntityFieldVertxParameterColumnMapper.class)
    MappedField1 mappedField1,
    @Mapping(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class)
    @Mapping(JdbcEntity.TestEntityFieldJdbcParameterColumnMapperNonFinal.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcResultColumnMapperNonFinal.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapperNonFinal.class)
    @Mapping(VertxEntity.TestEntityFieldVertxResultColumnMapperNonFinal.class)
    @Mapping(VertxEntity.TestEntityFieldVertxParameterColumnMapperNonFinal.class)
    MappedField2 mappedField2
) {

    public static final Map<String, String> initializedStaticField = new HashMap<>();

    public record UnknownTypeField() {}
    public record MappedField1() {}
    public record MappedField2() {}

    public static class TestUnknownType {}


    public static TestEntityRecord defaultRecord() {
        return new TestEntityRecord(
            "field1",
            42,
            43,
            new UnknownTypeField(),
            new MappedField1(),
            new MappedField2()
        );
    }
}
