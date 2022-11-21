package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.CassandraEntity.AllNativeTypesEntity;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CassandraExtensionTest {
    public CassandraExtensionTest() throws Exception {}


    private final ClassLoader cl = TestUtils.testKoraExtension(new TypeRef<?>[]{
            TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, TestEntityRecord.class)),
            TypeRef.of(CassandraRowMapper.class, TestEntityRecord.class),
            TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, TestEntityJavaBean.class)),
            TypeRef.of(CassandraRowMapper.class, TestEntityJavaBean.class),
            TypeRef.of(CassandraResultSetMapper.class, TypeRef.of(List.class, AllNativeTypesEntity.class)),
            TypeRef.of(CassandraRowMapper.class, AllNativeTypesEntity.class),
        },
        TypeRef.of(CassandraRowColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
        TypeRef.of(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class)
    );


    @Test
    @SuppressWarnings("unchecked")
    void testEntityRecordListResultSetMapper() throws Exception {
        var type = cl.loadClass("ru.tinkoff.kora.database.common.annotation.processor.entity.$TestEntityRecord_ListCassandraResultSetMapper");
        assertThat(type)
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraResultSetMapper.class));

        var columnDefinition = Mockito.mock(ColumnDefinitions.class);
        var rs = Mockito.mock(ResultSet.class);
        var unknownFieldMapper = (CassandraRowColumnMapper<TestEntityRecord.UnknownTypeField>) Mockito.mock(CassandraRowColumnMapper.class);
        var mappedField2Mapper = Mockito.mock(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class);
        var mapper = (CassandraResultSetMapper<List<TestEntityRecord>>) type.getConstructor(CassandraRowColumnMapper.class, CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class)
            .newInstance(unknownFieldMapper, mappedField2Mapper);

        when(rs.getColumnDefinitions()).thenReturn(columnDefinition);
        when(columnDefinition.firstIndexOf("field1")).thenReturn(0);
        when(columnDefinition.firstIndexOf("field2")).thenReturn(1);
        when(columnDefinition.firstIndexOf("field3")).thenReturn(2);
        when(columnDefinition.firstIndexOf("unknown_type_field")).thenReturn(3);
        when(columnDefinition.firstIndexOf("mapped_field1")).thenReturn(4);
        when(columnDefinition.firstIndexOf("mapped_field2")).thenReturn(5);

        var row = Mockito.mock(Row.class);

        when(rs.iterator()).thenReturn(List.of(row).iterator());
        var result = mapper.apply(rs);
        assertThat(result).hasSize(1);

        verify(row).getString(0);
        verify(row).getInt(1);
        verify(row).getInt(2);
        verify(unknownFieldMapper).apply(row, 3);
        verify(mappedField2Mapper).apply(row, 5);
    }

    @Test
    void testEntityRecordRowMapper() throws Exception {
        assertThat(cl.loadClass("ru.tinkoff.kora.database.common.annotation.processor.entity.$TestEntityRecord_CassandraRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(CassandraRowMapper.class));
    }

    private static Predicate<Class<?>> doesImplement(Class<?> anInterface) {
        return aClass -> {
            for (var aClassInterface : aClass.getInterfaces()) {
                if (aClassInterface.equals(anInterface)) {
                    return true;
                }
            }
            return false;
        };
    }
}
