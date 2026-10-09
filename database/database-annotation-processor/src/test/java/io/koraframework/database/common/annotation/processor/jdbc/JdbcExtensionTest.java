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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            record Order(@Id long id, @Column("user_id") String userId, @org.jspecify.annotations.Nullable String number) {}
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
        Mockito.when(rs.next()).thenReturn(true, true, true, false);
        Mockito.when(rs.findColumn("u_id")).thenReturn(1);
        Mockito.when(rs.findColumn("u_name")).thenReturn(2);
        Mockito.when(rs.findColumn("o_id")).thenReturn(3);
        Mockito.when(rs.findColumn("o_user_id")).thenReturn(4);
        Mockito.when(rs.findColumn("o_number")).thenReturn(5);
        Mockito.when(rs.getString(1)).thenReturn("u1", "u2", "u2");
        Mockito.when(rs.getString(2)).thenReturn("User 1", "User 2", "User 2");
        Mockito.when(rs.getLong(3)).thenReturn(0L, 1L, 2L);
        Mockito.when(rs.getString(4)).thenReturn(null, "u2", "u2");
        Mockito.when(rs.getString(5)).thenReturn(null, null, "n2");
        Mockito.when(rs.wasNull()).thenReturn(
            false, false, true, true, true,
            false, false, false, false, true,
            false, false, false, false, false
        );

        var result = (List<?>) mapper.apply(rs);

        assertThat(result).hasSize(2);
        var orders = result.get(0).getClass().getMethod("orders");
        orders.setAccessible(true);
        assertThat((List<?>) orders.invoke(result.get(0))).isEmpty();
        var secondOrders = (List<?>) orders.invoke(result.get(1));
        assertThat(secondOrders).hasSize(2);
        var number = secondOrders.get(0).getClass().getMethod("number");
        number.setAccessible(true);
        assertThat(number.invoke(secondOrders.get(0))).isNull();
        assertThat(number.invoke(secondOrders.get(1))).isEqualTo("n2");
    }

    @Test
    public void testOneToManyResultSetMapperAggregatesRows() throws Exception {
        compileUserOrdersView("record UserOrdersView(@Embedded(\"u_\") User user, @Embedded(\"o_\") java.util.List<Order> orders) {}");

        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$UserOrdersView_JdbcResultSetMapper").getConstructor().newInstance();
        var rs = userOrdersResultSet(List.of("u1", "u1"));

        var result = mapper.apply(rs);

        assertThat(result).isNotNull();
        var orders = result.getClass().getMethod("orders");
        orders.setAccessible(true);
        assertThat((List<?>) orders.invoke(result)).hasSize(2);

        var emptyRs = Mockito.mock(ResultSet.class);
        Mockito.when(emptyRs.next()).thenReturn(false);
        assertThat(mapper.apply(emptyRs)).isNull();
    }

    @Test
    public void testOneToManyResultSetMapperRejectsSeveralRoots() throws Exception {
        compileUserOrdersView("record UserOrdersView(@Embedded(\"u_\") User user, @Embedded(\"o_\") java.util.List<Order> orders) {}");

        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$UserOrdersView_JdbcResultSetMapper").getConstructor().newInstance();
        var rs = userOrdersResultSet(List.of("u1", "u2"));

        assertThatThrownBy(() -> mapper.apply(rs))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("ResultSet was expected to return zero or one root entity but got two or more");
    }

    @Test
    public void testOneToManyListResultSetMapperSupportsRecordCopyingCollection() throws Exception {
        compileUserOrdersView("""
            record UserOrdersView(@Embedded("u_") User user, @Embedded("o_") java.util.List<Order> orders) {
                UserOrdersView {
                    orders = java.util.List.copyOf(orders);
                }
            }
            """);

        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$UserOrdersView_ListJdbcResultSetMapper").getConstructor().newInstance();
        var rs = userOrdersResultSet(List.of("u1", "u1"));

        var result = (List<?>) mapper.apply(rs);

        assertThat(result).hasSize(1);
        var orders = result.get(0).getClass().getMethod("orders");
        orders.setAccessible(true);
        assertThat((List<?>) orders.invoke(result.get(0))).hasSize(2);
    }

    @Test
    public void testOneToManyListResultSetMapperGroupsByCompositeKeyWithNullPart() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            import io.koraframework.database.common.annotation.*;
            @Table("users")
            record User(@Id String tenant, @Nullable @Id String id, String name) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("orders")
            record Order(@Id long id, String number) {}
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
        var lastWasNull = new java.util.concurrent.atomic.AtomicBoolean();
        Mockito.when(rs.next()).thenReturn(true, true, false);
        Mockito.when(rs.findColumn("u_tenant")).thenReturn(1);
        Mockito.when(rs.findColumn("u_id")).thenReturn(2);
        Mockito.when(rs.findColumn("u_name")).thenReturn(3);
        Mockito.when(rs.findColumn("o_id")).thenReturn(4);
        Mockito.when(rs.findColumn("o_number")).thenReturn(5);
        Mockito.when(rs.getString(Mockito.anyInt())).thenAnswer(invocation -> {
            int index = invocation.getArgument(0);
            lastWasNull.set(index == 2);
            return switch (index) {
                case 1 -> "t1";
                case 3 -> "User";
                case 5 -> "n";
                default -> null;
            };
        });
        Mockito.when(rs.getLong(4)).thenAnswer(invocation -> {
            lastWasNull.set(false);
            return 1L;
        });
        Mockito.when(rs.wasNull()).thenAnswer(invocation -> lastWasNull.get());

        var result = (List<?>) mapper.apply(rs);

        assertThat(result).hasToString("[UserOrdersView[user=User[tenant=t1, id=null, name=User], orders=[Order[id=1, number=n], Order[id=1, number=n]]]]");
    }

    private void compileUserOrdersView(String view) {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            import io.koraframework.database.common.annotation.*;
            @Table("users")
            record User(@Id String id, String name) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("orders")
            record Order(@Id long id, @Column("user_id") String userId, String number) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            @EntityJdbc
            """ + view
        );
        compileResult.assertSuccess();
    }

    private static ResultSet userOrdersResultSet(List<String> userIds) throws Exception {
        var rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.next()).thenReturn(true, true, false);
        Mockito.when(rs.findColumn("u_id")).thenReturn(1);
        Mockito.when(rs.findColumn("u_name")).thenReturn(2);
        Mockito.when(rs.findColumn("o_id")).thenReturn(3);
        Mockito.when(rs.findColumn("o_user_id")).thenReturn(4);
        Mockito.when(rs.findColumn("o_number")).thenReturn(5);
        Mockito.when(rs.getString(1)).thenReturn(userIds.get(0), userIds.get(1));
        Mockito.when(rs.getString(2)).thenReturn("User");
        Mockito.when(rs.getLong(3)).thenReturn(1L, 2L);
        Mockito.when(rs.getString(4)).thenReturn(userIds.get(0), userIds.get(1));
        Mockito.when(rs.getString(5)).thenReturn("n1", "n2");
        Mockito.when(rs.wasNull()).thenReturn(false);
        return rs;
    }

    @Test
    public void testColumnMapperValueForSqlNullKeepsAbsentEmbeddedDetection() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            public final class TagsMapper implements JdbcResultColumnMapper<java.util.List<String>> {
                @Override
                public java.util.List<String> apply(ResultSet rs, int index) throws SQLException {
                    var array = rs.getArray(index);
                    return array == null ? java.util.List.of() : java.util.List.of((String[]) array.getArray());
                }
            }
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("users")
            record User(@Id String id, String name) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("orders")
            record Order(@Id long id, @Mapping(TagsMapper.class) java.util.List<String> tags) {}
            """,
            """
            record Tagged(@Mapping(TagsMapper.class) java.util.List<String> tags) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            @EntityJdbc
            record UserOrdersView(@Embedded("u_") User user, @Embedded("o_") java.util.List<Order> orders, @Nullable @Embedded("t_") Tagged tagged) {}
            """
        );
        compileResult.assertSuccess();

        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$UserOrdersView_ListJdbcResultSetMapper").getConstructor().newInstance();
        var rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.next()).thenReturn(true, false);
        Mockito.when(rs.findColumn("u_id")).thenReturn(1);
        Mockito.when(rs.findColumn("u_name")).thenReturn(2);
        Mockito.when(rs.findColumn("o_id")).thenReturn(3);
        Mockito.when(rs.findColumn("o_tags")).thenReturn(4);
        Mockito.when(rs.findColumn("t_tags")).thenReturn(5);
        Mockito.when(rs.getString(1)).thenReturn("u1");
        Mockito.when(rs.getString(2)).thenReturn("User");
        // LEFT JOIN without a child row and an absent nullable embedded: every o_* and t_* column is NULL
        Mockito.when(rs.wasNull()).thenReturn(false, false, true, true, true);

        var result = mapper.apply(rs);

        assertThat(result).hasToString("[UserOrdersView[user=User[id=u1, name=User], orders=[], tagged=null]]");
    }

    @Test
    public void testColumnMapperValueForSqlNullIsKept() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            public final class TagsMapper implements JdbcResultColumnMapper<java.util.List<String>> {
                @Override
                public java.util.List<String> apply(ResultSet rs, int index) throws SQLException {
                    var array = rs.getArray(index);
                    return array == null ? java.util.List.of() : java.util.List.of((String[]) array.getArray());
                }
            }
            """,
            """
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            import java.util.List;
            @EntityJdbc
            record TestEntity(long id, @Mapping(TagsMapper.class) List<String> tags, @Nullable @Mapping(TagsMapper.class) List<String> otherTags) {}
            """
        );
        compileResult.assertSuccess();

        var mapper = (JdbcRowMapper<?>) compileResult.loadClass("$TestEntity_JdbcRowMapper").getConstructor().newInstance();
        var rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.findColumn("id")).thenReturn(1);
        Mockito.when(rs.findColumn("tags")).thenReturn(2);
        Mockito.when(rs.findColumn("other_tags")).thenReturn(3);
        Mockito.when(rs.getLong(1)).thenReturn(1L);
        Mockito.when(rs.wasNull()).thenReturn(false, true);

        var result = mapper.apply(rs);

        assertThat(result).hasToString("TestEntity[id=1, tags=[], otherTags=[]]");
    }

    @Test
    public void testOneToManyListResultSetMapperRejectsPartiallyNullChild() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            import io.koraframework.database.common.annotation.*;
            @Table("users")
            record User(@Id String id, String name) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            @Table("orders")
            record Order(@Id long id, @Column("user_id") String userId, String number) {}
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
        Mockito.when(rs.next()).thenReturn(true, false);
        Mockito.when(rs.findColumn("u_id")).thenReturn(1);
        Mockito.when(rs.findColumn("u_name")).thenReturn(2);
        Mockito.when(rs.findColumn("o_id")).thenReturn(3);
        Mockito.when(rs.findColumn("o_user_id")).thenReturn(4);
        Mockito.when(rs.findColumn("o_number")).thenReturn(5);
        Mockito.when(rs.getString(1)).thenReturn("u1");
        Mockito.when(rs.getString(2)).thenReturn("User 1");
        Mockito.when(rs.getLong(3)).thenReturn(1L);
        Mockito.when(rs.getString(5)).thenReturn("n1");
        Mockito.when(rs.wasNull()).thenReturn(false, false, false, true, false);

        assertThatThrownBy(() -> mapper.apply(rs))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Field userId is not nullable, but column o_user_id is null");
    }

    @Test
    public void testOneToManyListResultSetMapperHandlesEmptySingleBoxedFieldChild() throws Exception {
        compile(List.of(new JdbcEntityAnnotationProcessor()),
            """
            import io.koraframework.database.common.annotation.*;
            @Table("children")
            record Child(@Id Long id) {}
            """,
            """
            import io.koraframework.database.common.annotation.*;
            import io.koraframework.database.jdbc.annotation.EntityJdbc;
            @EntityJdbc
            record ParentChildren(@Id String id, @Embedded("c_") java.util.List<Child> children) {}
            """
        );

        compileResult.assertSuccess();
        var mapper = (JdbcResultSetMapper<?>) compileResult.loadClass("$ParentChildren_ListJdbcResultSetMapper").getConstructor().newInstance();
        var rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.next()).thenReturn(true, false);
        Mockito.when(rs.findColumn("id")).thenReturn(1);
        Mockito.when(rs.findColumn("c_id")).thenReturn(2);
        Mockito.when(rs.getString(1)).thenReturn("p1");
        Mockito.when(rs.getLong(2)).thenReturn(0L);
        Mockito.when(rs.wasNull()).thenReturn(false, true);

        var result = (List<?>) mapper.apply(rs);

        assertThat(result).hasSize(1);
        var children = result.get(0).getClass().getMethod("children");
        children.setAccessible(true);
        assertThat((List<?>) children.invoke(result.get(0))).isEmpty();
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
    public void testResultSetMapperOfArrayTypes() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface Application extends JdbcDatabaseModule {
                @Root
                default String root(JdbcResultSetMapper<java.util.List<byte[]>> list, JdbcResultSetMapper<byte[]> single) {
                    return "";
                }
            }
            """);

        compileResult.assertSuccess();
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
