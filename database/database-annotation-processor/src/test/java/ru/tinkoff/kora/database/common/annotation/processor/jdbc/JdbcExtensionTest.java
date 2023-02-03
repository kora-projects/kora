package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcExtensionTest {
    public JdbcExtensionTest() throws Exception {}

    private final ClassLoader cl = TestUtils.testKoraExtension(
        new TypeRef<?>[]{
            TypeRef.of(JdbcRowMapper.class, TestEntityRecord.class),
            TypeRef.of(JdbcResultSetMapper.class, TestEntityRecord.class),
            TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, TestEntityRecord.class)),
            TypeRef.of(JdbcRowMapper.class, TestEntityJavaBean.class),
            TypeRef.of(JdbcResultSetMapper.class, TestEntityJavaBean.class),
            TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, TestEntityJavaBean.class)),
            TypeRef.of(JdbcRowMapper.class, JdbcEntity.AllNativeTypesEntity.class),
            TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, JdbcEntity.AllNativeTypesEntity.class)),
        },
        TypeRef.of(JdbcResultColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
        TypeRef.of(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class)
    );


    @Test
    @SuppressWarnings("unchecked")
    void testRowMapper() throws Exception {
        JdbcResultColumnMapper<TestEntityRecord.UnknownTypeField> unknownFieldMapper = Mockito.mock(JdbcResultColumnMapper.class);
        var mappedField2Mapper = Mockito.mock(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class);
        var jdbcRowMapper = (JdbcRowMapper<TestEntityRecord>) cl.loadClass("ru.tinkoff.kora.database.common.annotation.processor.entity.$TestEntityRecord_JdbcRowMapper")
            .getConstructor(JdbcResultColumnMapper.class, JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class)
            .newInstance(unknownFieldMapper, mappedField2Mapper);

        var row = Mockito.mock(ResultSet.class);
        var ref = TestEntityRecord.defaultRecord();
        when(row.findColumn("field1")).thenReturn(1);
        when(row.findColumn("field2")).thenReturn(2);
        when(row.findColumn("field3")).thenReturn(3);
        when(row.findColumn("unknown_type_field")).thenReturn(4);
        when(row.findColumn("mapped_field1")).thenReturn(5);
        when(row.findColumn("mapped_field2")).thenReturn(6);

        when(row.wasNull()).thenReturn(false, false, false, false, false, false);
        when(row.getString(1)).thenReturn(ref.field1());
        when(row.getInt(2)).thenReturn(ref.field2());
        when(row.getInt(3)).thenReturn(ref.field3());
        when(unknownFieldMapper.apply(any(), eq(4))).thenReturn(new TestEntityRecord.UnknownTypeField());
        when(mappedField2Mapper.apply(any(), eq(6))).thenReturn(new TestEntityRecord.MappedField2());

        var result = jdbcRowMapper.apply(row);

        verify(row).findColumn("field1");
        verify(row).findColumn("field2");
        verify(row).findColumn("field3");
        verify(row).findColumn("unknown_type_field");
        verify(row).findColumn("mapped_field1");
        verify(row).findColumn("mapped_field2");
        verify(unknownFieldMapper).apply(any(), eq(4));
        verify(mappedField2Mapper).apply(any(), eq(6));

        Assertions.assertThat(result).isEqualTo(ref);
    }
}
