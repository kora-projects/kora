package ru.tinkoff.kora.database.common.annotation.processor.entity;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.JdbcEntity;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.R2dbcEntity;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.VertxEntity;

import javax.annotation.Nullable;

public class TestEntityJavaBean {
    private String field1;
    private int field2;
    @Nullable
    private Integer field3;
    private TestEntityRecord.UnknownTypeField unknownTypeField;
    @Mapping(JdbcEntity.TestEntityFieldJdbcResultColumnMapper.class)
    @Mapping(JdbcEntity.TestEntityFieldJdbcParameterColumnMapper.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraResultColumnMapper.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraParameterColumnMapper.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcResultColumnMapper.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapper.class)
    @Mapping(VertxEntity.TestEntityFieldVertxResultColumnMapper.class)
    @Mapping(VertxEntity.TestEntityFieldVertxParameterColumnMapper.class)
    private TestEntityRecord.MappedField1 mappedField1;
    @Mapping(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class)
    @Mapping(JdbcEntity.TestEntityFieldJdbcParameterColumnMapperNonFinal.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class)
    @Mapping(CassandraEntity.TestEntityFieldCassandraParameterColumnMapperNonFinal.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcResultColumnMapperNonFinal.class)
    @Mapping(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapperNonFinal.class)
    @Mapping(VertxEntity.TestEntityFieldVertxResultColumnMapperNonFinal.class)
    @Mapping(VertxEntity.TestEntityFieldVertxParameterColumnMapperNonFinal.class)
    private TestEntityRecord.MappedField2 mappedField2;

    public static TestEntityJavaBean defaultJavaBean() {
        var v = new TestEntityJavaBean();
        v.field1 = "field1";
        v.field2 = 42;
        v.field3 = 43;
        v.unknownTypeField = new TestEntityRecord.UnknownTypeField();
        v.mappedField1 = new TestEntityRecord.MappedField1();
        v.mappedField2 = new TestEntityRecord.MappedField2();
        return v;
    }


    public String getField1() {
        return field1;
    }

    public void setField1(String field1) {
        this.field1 = field1;
    }

    public int getField2() {
        return field2;
    }

    public void setField2(int field2) {
        this.field2 = field2;
    }

    @Nullable
    public Integer getField3() {
        return field3;
    }

    public void setField3(@Nullable Integer field3) {
        this.field3 = field3;
    }

    public TestEntityRecord.UnknownTypeField getUnknownTypeField() {
        return unknownTypeField;
    }

    public void setUnknownTypeField(TestEntityRecord.UnknownTypeField unknownTypeField) {
        this.unknownTypeField = unknownTypeField;
    }

    public TestEntityRecord.MappedField1 getMappedField1() {
        return mappedField1;
    }

    public void setMappedField1(TestEntityRecord.MappedField1 mappedField1) {
        this.mappedField1 = mappedField1;
    }

    public TestEntityRecord.MappedField2 getMappedField2() {
        return mappedField2;
    }

    public void setMappedField2(TestEntityRecord.MappedField2 mappedField2) {
        this.mappedField2 = mappedField2;
    }
}
