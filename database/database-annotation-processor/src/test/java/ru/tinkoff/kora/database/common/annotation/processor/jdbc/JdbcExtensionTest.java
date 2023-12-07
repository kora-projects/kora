package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.sql.ResultSet;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class JdbcExtensionTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
                """
                import ru.tinkoff.kora.database.jdbc.*;
                import ru.tinkoff.kora.database.jdbc.mapper.result.*;
                import ru.tinkoff.kora.database.jdbc.mapper.parameter.*;
                import ru.tinkoff.kora.common.Mapping;
                import java.sql.*;
                """;
    }

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
                TypeRef.of(JdbcResultSetMapper.class, TypeRef.of(List.class, String.class)),
            },
            TypeRef.of(JdbcResultColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
            TypeRef.of(JdbcEntity.TestEntityFieldJdbcResultColumnMapperNonFinal.class),
            TypeRef.of(JdbcRowMapper.class, String.class)
        );
    }

    public record TestRow(String f1, String f2) {}

    @Test
    void testRowMapperWithTags() {
        compile(List.of(new KoraAppProcessor(), new RepositoryAnnotationProcessor()),
                """
                @KoraApp
                public interface Application extends JdbcDatabaseModule {
                
                    @Root
                    default String testRowMapper(JdbcResultSetMapper<TestRow> tm) {
                        return "";
                    }
                    
                    @Tag(String.class)
                    default JdbcResultColumnMapper<String> taggedMapper() {
                        return ResultSet::getString;
                    }
                }
                """,
                """
                record TestRow(String f1, String f2, @Tag(String.class) String f3, @Mapping(TestRowResultColumnMapper.class) String f4) { }
                """,
                """
                public final class TestRowResultColumnMapper implements JdbcResultColumnMapper<String> {
                    @Override
                    public String apply(ResultSet row, int index) throws SQLException {
                        return row.getString(index);
                    }
                }
                """
        );

        compileResult.assertSuccess();
        var graph = loadGraphDraw("Application");
        Assertions.assertThat(graph.getNodes()).hasSize(4);

        var mapper = compileResult.loadClass("$TestRow_JdbcRowMapper");
        var constructor = mapper.getConstructors()[0];
        Assertions.assertThat(constructor.getParameters()).hasSize(1);

        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()).hasSize(1);
        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()[0]).isInstanceOf(Tag.class);
    }

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

    @Test
    public void testRowMapperWithTaggedField() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
                @ru.tinkoff.kora.common.Tag(TestRecord.class)
                default ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper<String> taggedColumnMapper() {
                    return java.sql.ResultSet::getString;
                }

              @Root
              default String root(ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper<TestRecord> r) {return "";}
            }
            """, """
            public record TestRecord(@ru.tinkoff.kora.common.Tag(TestRecord.class) String value) {}
            """);

        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRecord_JdbcRowMapper"))
            .isNotNull()
            .isFinal()
            .matches(doesImplement(JdbcRowMapper.class));
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
