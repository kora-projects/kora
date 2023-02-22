package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class JdbcExtensionTest {
    @Test
    void testTypes() throws Exception {
        TestUtils.testKoraExtension(
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
    }

    public record TestRow(String f1, String f2) {}

    @Test
    void testRowMapper() throws Exception {
        var cl = TestUtils.testKoraExtension(new TypeRef<?>[]{
                TypeRef.of(JdbcResultSetMapper.class, TestRow.class),
            }
        );
        var k = cl.loadClass("ru.tinkoff.kora.database.common.annotation.processor.jdbc.$JdbcExtensionTest_TestRow_JdbcRowMapper");
        var mapper = (JdbcRowMapper<TestRow>) k.getConstructors()[0].newInstance();
        var rs = mock(ResultSet.class);

        when(rs.findColumn("f1")).thenReturn(1);
        when(rs.findColumn("f2")).thenReturn(2);
        when(rs.getString(1)).thenReturn("test1");
        when(rs.getString(2)).thenReturn("test2");
        var o1 = mapper.apply(rs);
        assertThat(o1).isEqualTo(new TestRow("test1", "test2"));
        verify(rs).getString(1);
        verify(rs).getString(2);
    }

    @Test
    void testListResultSetMapper() throws Exception {
        var cl = TestUtils.testKoraExtension(new TypeRef<?>[]{
                TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, TestRow.class)),
            }
        );
        var k = cl.loadClass("ru.tinkoff.kora.database.common.annotation.processor.jdbc.$JdbcExtensionTest_TestRow_ListJdbcResultSetMapper");
        var mapper = (JdbcResultSetMapper<List<TestRow>>) k.getConstructors()[0].newInstance();
        var rs = mock(ResultSet.class);

        when(rs.next()).thenReturn(true, true, false);
        when(rs.findColumn("f1")).thenReturn(1);
        when(rs.findColumn("f2")).thenReturn(2);
        when(rs.getString(1)).thenReturn("test1");
        when(rs.getString(2)).thenReturn("test2");

        var o1 = mapper.apply(rs);

        assertThat(o1).isEqualTo(List.of(new TestRow("test1", "test2"), new TestRow("test1", "test2")));
        verify(rs, times(2)).getString(1);
        verify(rs, times(2)).getString(2);
        verify(rs, times(3)).next();
    }
}
