package io.koraframework.database.common.annotation.processor.jdbc;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.common.annotation.Tag;
import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import io.koraframework.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcExtensionTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
            """
                import io.koraframework.database.jdbc.*;
                import io.koraframework.database.jdbc.annotation.*;
                import io.koraframework.database.jdbc.mapper.result.*;
                import io.koraframework.database.jdbc.mapper.parameter.*;
                import io.koraframework.common.annotation.Mapping;
                import java.sql.*;
                """;
    }

    @Test
    public void testOneToManyListResultSetMapperGenerated() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            @Table("users")
            record User(@Id String id, String name) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("orders")
            record Order(@Id String id, @Column("user_id") String userId, String number) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            @EntityJdbc
            record UserOrdersView(@Embedded("u_") User user, @Embedded("o_") java.util.List<Order> orders) {}
            """
        );

        compileResult.assertSuccess();
        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$UserOrdersView_ListJdbcResultSetMapper").getConstructor().newInstance();
        var rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.next()).thenReturn(true, true, false);
        Mockito.when(rs.findColumn("u_id")).thenReturn(1);
        Mockito.when(rs.findColumn("u_name")).thenReturn(2);
        Mockito.when(rs.findColumn("o_id")).thenReturn(3);
        Mockito.when(rs.findColumn("o_user_id")).thenReturn(4);
        Mockito.when(rs.findColumn("o_number")).thenReturn(5);
        Mockito.when(rs.getString(1)).thenReturn("u1", "u1");
        Mockito.when(rs.getString(2)).thenReturn("User 1", "User 1");
        Mockito.when(rs.getString(3)).thenReturn("o1", "o2");
        Mockito.when(rs.getString(4)).thenReturn("u1", "u1");
        Mockito.when(rs.getString(5)).thenReturn("n1", "n2");
        Mockito.when(rs.wasNull()).thenReturn(false, false, false, false, false, false, false, false, false, false);

        var result = (List<?>) mapper.apply(rs);

        assertThat(result).hasSize(1);
        var orders = result.get(0).getClass().getMethod("orders");
        orders.setAccessible(true);
        assertThat((List<?>) orders.invoke(result.get(0))).hasSize(2);
    }

    @Test
    public void testRowMapperWithTags() {
        compile(List.of(new KoraAppProcessor(), new RepositoryAnnotationProcessor(), new JdbcEntityAnnotationProcessor()),
            """
                import io.koraframework.common.annotation.Tag;@KoraApp
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
                import io.koraframework.common.annotation.Tag;@EntityJdbc record TestRow(String f1, String f2, @Tag(String.class) String f3, @Mapping(TestRowResultColumnMapper.class) String f4) { }
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
        Assertions.assertThat(graph.getNodes()).hasSize(3);

        var mapper = compileResult.loadClass("$TestRow_JdbcRowMapper");
        var constructor = mapper.getConstructors()[0];
        Assertions.assertThat(constructor.getParameters()).hasSize(1);

        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()).hasSize(1);
        Assertions.assertThat(constructor.getParameters()[0].getAnnotations()[0]).isInstanceOf(Tag.class);
    }

    @Test
    public void testRowMapperWithTaggedField() {
        compile(List.of(new KoraAppProcessor(), new JdbcEntityAnnotationProcessor()), """
            import io.koraframework.common.annotation.Tag;@io.koraframework.common.annotation.KoraApp
            public interface TestApp {
                @Tag(TestRecord.class)
                default io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper<String> taggedColumnMapper() {
                    return java.sql.ResultSet::getString;
                }
            
              @Root
              default String root(io.koraframework.database.jdbc.mapper.result.JdbcRowMapper<TestRecord> r) {return "";}
            }
            """, """
            import io.koraframework.common.annotation.Tag;@EntityJdbc public record TestRecord(@Tag(TestRecord.class) String value) {}
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
