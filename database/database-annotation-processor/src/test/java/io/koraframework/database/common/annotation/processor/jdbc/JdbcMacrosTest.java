package io.koraframework.database.common.annotation.processor.jdbc;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class JdbcMacrosTest extends AbstractJdbcRepositoryTest {

    @Test
    void returnTable() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}

                @Query("SELECT * FROM %{return#table} WHERE id = :id")
                @Nullable
                Entity findById(String id);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("findById", "1");
        verify(executor.mockConnection).prepareStatement("SELECT * FROM entities WHERE id = ?");
    }

    @Test
    void returnSelectsAndTable() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            
                @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
                @Nullable
                Entity findById(String id);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("findById", "1");
        verify(executor.mockConnection).prepareStatement("SELECT id, value1, value2, value3 FROM entities WHERE id = ?");
    }

    @Test
    void inserts() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?)");
    }

    @Test
    void insertBatch() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts}")
                UpdateCount insert(@Batch java.util.List<Entity> entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        when(executor.preparedStatement.executeLargeBatch()).thenReturn(new long[] {1L});
        repository.invoke("insert", List.of(newGeneratedObject("Entity", "1", 1, "1", "1").get()));
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?)");
    }

    @Test
    void insertsWithoutId() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts -= @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)");
    }

    @Test
    void columnsAndValuesWithoutId() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Query("INSERT INTO %{entity#table}(%{entity#columns -= @id}) VALUES (%{entity#values -= @id})")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)");
    }

    @Test
    void insertsExtended() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends ParentRepository<Entity> {
            
            }
            """, """
            public interface ParentRepository<T> extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts -= @id}")
                UpdateCount insert(T entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)");
    }

    @Test
    void insertsWithoutField() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts -= field1}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(id, value2, value3) VALUES (?, ?, ?)");
    }

    @Test
    void upsert() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET value1 = ?, value2 = ?, value3 = ?");
    }

    @Test
    void upsertBatch() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
                UpdateCount insert(@Batch java.util.List<Entity> entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        when(executor.preparedStatement.executeLargeBatch()).thenReturn(new long[] {1L});
        repository.invoke("insert", List.of(newGeneratedObject("Entity", "1", 1, "1", "1").get()));
        verify(executor.mockConnection).prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET value1 = ?, value2 = ?, value3 = ?");
    }

    @Test
    void entityTableAndUpdate() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id = ?");
    }

    @Test
    void entityTableAndUpdateBatch() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(@Batch java.util.List<Entity> entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        when(executor.preparedStatement.executeLargeBatch()).thenReturn(new long[] {1L});
        repository.invoke("insert", List.of(newGeneratedObject("Entity", "1", 1, "1", "1").get()));
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbedded() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, String id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", "2").get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedNullable() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded @Nullable EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, String id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", "2").get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedParamNullable() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, @Nullable String id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", "2").get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedNullanleParamNullable() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded @Nullable EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, @Nullable String id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", "2").get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    public static final class TimeJdbcResultColumnMapper implements JdbcResultColumnMapper<OffsetDateTime> {
        @Override
        public OffsetDateTime apply(ResultSet row, int index) throws SQLException {
            return row.getObject(index, OffsetDateTime.class);
        }
    }

    public static final class TimeJdbcParameterColumnMapper implements JdbcParameterColumnMapper<OffsetDateTime> {
        @Override
        public void set(PreparedStatement stmt, int index, @Nullable OffsetDateTime value) throws SQLException {
            stmt.setObject(index, value);
        }
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedWithMapper() throws SQLException {
        var repository = compileJdbc(List.of(new TimeJdbcParameterColumnMapper()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, java.time.OffsetDateTime id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", OffsetDateTime.MIN).get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedNullableWithMapper() throws SQLException {
        var repository = compileJdbc(List.of(new TimeJdbcParameterColumnMapper()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
            @Table("entities")
            record Entity(@Id @Embedded @Nullable EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
            record EntityId(String id1, java.time.OffsetDateTime id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", OffsetDateTime.MIN).get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedNullableParamWithMapper() throws SQLException {
        var repository = compileJdbc(List.of(new TimeJdbcParameterColumnMapper()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id @Embedded EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
                record EntityId(String id1, java.time.@Nullable OffsetDateTime id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", OffsetDateTime.MIN).get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateWhereIdIsEmbeddedNullableParamNullableWithMapper() throws SQLException {
        var repository = compileJdbc(List.of(new TimeJdbcParameterColumnMapper()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id @Embedded @Nullable EntityId id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """, """
                record EntityId(String id1, java.time.@Nullable OffsetDateTime id2) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", newGeneratedObject("EntityId", "1", OffsetDateTime.MIN).get(), 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?");
    }

    @Test
    void entityTableAndUpdateExclude() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates -= field1} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value2 = ?, value3 = ? WHERE id = ?");
    }

    @Test
    void entityTableAndUpdateInclude() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates = field1} WHERE %{entity#where = @id}")
                UpdateCount insert(Entity entity);
            }
            """, """
                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            """);

        repository.invoke("insert", newGeneratedObject("Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ? WHERE id = ?");
    }

    @Test
    void returnEmbeddedSelectsWithTableAliases() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Table("users")
                record User(@Id String id, String name) {}

                @Table("orders")
                record Order(@Id String id, @Column("user_id") String userId, String number) {}

                record UserOrderView(@Embedded("u_") User user, @Embedded("o_") Order order) {}

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                @Nullable
                UserOrderView find(String id);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.UserOrderView> {
                public TestRepository.UserOrderView apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("find", "1");
        verify(executor.mockConnection).prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u JOIN orders o ON o.user_id = u.id WHERE u.id = ?");
    }

    @Test
    void nestedReturnTargetSelectsWithTableAliases() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Table("users")
                record User(@Id String id, String name) {}

                @Table("orders")
                record Order(@Id String id, @Column("user_id") String userId, String number) {}

                record UserOrderView(@Embedded("u_") User user, @Embedded("o_") Order order) {}

                @Query("SELECT %{return.user#selects}, %{return.order#selects} FROM %{return.user#table as u} JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                @Nullable
                UserOrderView find(String id);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.UserOrderView> {
                public TestRepository.UserOrderView apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("find", "1");
        verify(executor.mockConnection).prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u JOIN orders o ON o.user_id = u.id WHERE u.id = ?");
    }

    @Test
    void entityWhereIdWithTableAlias() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}

                @Query("SELECT %{return#selects} FROM %{entity#table as e} WHERE %{entity#where = @id}")
                @Nullable
                Entity find(Entity entity);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("find", newGeneratedObject("TestRepository$Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("SELECT id, value1, value2, value3 FROM entities e WHERE e.id = ?");
    }

    @Test
    void leftJoinNullableEmbeddedEntity() throws Exception {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} LEFT JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                @Nullable
                UserOrderView find(String id);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<UserOrderView> {
                public UserOrderView apply(ResultSet rs) throws SQLException {
                    return new UserOrderView(new User(rs.getString(1), rs.getString(2)), null);
                }
            }
            """, """
            @Table("users")
            record User(@Id String id, String name) {}
            """, """
            @Table("orders")
            record Order(@Id String id, @Column("user_id") String userId, String number) {}
            """, """
            record UserOrderView(@Embedded("u_") User user, @Nullable @Embedded("o_") Order order) {}
            """);

        when(executor.resultSet.next()).thenReturn(true, false);
        when(executor.resultSet.findColumn("u_id")).thenReturn(1);
        when(executor.resultSet.findColumn("u_name")).thenReturn(2);
        when(executor.resultSet.findColumn("o_id")).thenReturn(3);
        when(executor.resultSet.findColumn("o_user_id")).thenReturn(4);
        when(executor.resultSet.findColumn("o_number")).thenReturn(5);
        when(executor.resultSet.getString(1)).thenReturn("u1");
        when(executor.resultSet.getString(2)).thenReturn("User 1");
        when(executor.resultSet.getString(3)).thenReturn(null);
        when(executor.resultSet.getString(4)).thenReturn(null);
        when(executor.resultSet.getString(5)).thenReturn(null);
        when(executor.resultSet.wasNull()).thenReturn(false, false, true, true, true);

        var result = repository.invoke("find", "u1");

        assertThat(result).isNotNull();
        var order = result.getClass().getMethod("order");
        order.setAccessible(true);
        assertThat(order.invoke(result)).isNull();
        verify(executor.mockConnection).prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u LEFT JOIN orders o ON o.user_id = u.id WHERE u.id = ?");
    }

    @Test
    void oneToManyEmbeddedCollectionMapping() throws Exception {
        var repository = compileJdbc(List.of(newGeneratedObject("TestResultSetMapper")), """
            @Repository
            public interface TestRepository extends JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} LEFT JOIN %{return.orders#table as o} ON o.user_id = u.id")
                java.util.List<UserOrdersView> find();
            }
            """, """
            public class TestResultSetMapper implements JdbcResultSetMapper<java.util.List<UserOrdersView>> {
                public java.util.List<UserOrdersView> apply(ResultSet rs) throws SQLException {
                    return java.util.List.of(new UserOrdersView(new User("u1", "User 1"), java.util.List.of(new Order("o1", "u1", "n1"), new Order("o2", "u1", "n2"))));
                }
            }
            """, """
            @Table("users")
            record User(@Id String id, String name) {}
            """, """
            @Table("orders")
            record Order(@Id String id, @Column("user_id") String userId, String number) {}
            """, """
            record UserOrdersView(@Embedded("u_") User user, @Embedded("o_") java.util.List<Order> orders) {}
            """);

        when(executor.resultSet.next()).thenReturn(true, true, false);
        when(executor.resultSet.findColumn("u_id")).thenReturn(1);
        when(executor.resultSet.findColumn("u_name")).thenReturn(2);
        when(executor.resultSet.findColumn("o_id")).thenReturn(3);
        when(executor.resultSet.findColumn("o_user_id")).thenReturn(4);
        when(executor.resultSet.findColumn("o_number")).thenReturn(5);
        when(executor.resultSet.getString(1)).thenReturn("u1", "u1");
        when(executor.resultSet.getString(2)).thenReturn("User 1", "User 1");
        when(executor.resultSet.getString(3)).thenReturn("o1", "o2");
        when(executor.resultSet.getString(4)).thenReturn("u1", "u1");
        when(executor.resultSet.getString(5)).thenReturn("n1", "n2");
        when(executor.resultSet.wasNull()).thenReturn(false, false, false, false, false, false, false, false, false, false);

        var result = (List<?>) repository.invoke("find");

        assertThat(result).hasSize(1);
        var ordersMethod = result.get(0).getClass().getMethod("orders");
        ordersMethod.setAccessible(true);
        var orders = (List<?>) ordersMethod.invoke(result.get(0));
        assertThat(orders).hasSize(2);
        verify(executor.mockConnection).prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u LEFT JOIN orders o ON o.user_id = u.id");
    }

    @Test
    void typeUseColumnArgumentWhere() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends AbstractJdbcRepository<@Column("id") String, TestRepository.Entity> {

                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            }
            """, """
            public interface AbstractJdbcRepository<K, V> extends JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return#table} WHERE %{keyArg#where}")
                @Nullable
                V findById(K keyArg);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("findById", "1");
        verify(executor.mockConnection).prepareStatement("SELECT id, value1, value2, value3 FROM entities WHERE id = ?");
    }

    @Test
    void genericTypeArgumentSelectsAndTable() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends AbstractJdbcRepository<TestRepository.Entity> {

                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            }
            """, """
            public interface AbstractJdbcRepository<V> extends JdbcRepository {

                @Query("SELECT %{V#selects} FROM %{V#table}")
                @Nullable
                V findOne();
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("findOne");
        verify(executor.mockConnection).prepareStatement("SELECT id, value1, value2, value3 FROM entities");
    }

    @Test
    void genericTypeArgumentWhereId() throws SQLException {
        var repository = compileJdbc(List.of(newGeneratedObject("TestRowMapper")), """
            @Repository
            public interface TestRepository extends AbstractJdbcRepository<TestRepository.Entity> {

                @Table("entities")
                record Entity(@Id String id, @Column("value1") int field1, String value2, @Nullable String value3) {}
            }
            """, """
            public interface AbstractJdbcRepository<V> extends JdbcRepository {

                @Query("SELECT %{V#selects} FROM %{V#table} WHERE %{V#where = @id}")
                @Nullable
                V findByEntity(V entity);
            }
            """, """
            public class TestRowMapper implements JdbcResultSetMapper<TestRepository.Entity> {
                public TestRepository.Entity apply(ResultSet rs) {
                  return null;
                }
            }
            """);

        repository.invoke("findByEntity", newGeneratedObject("TestRepository$Entity", "1", 1, "1", "1").get());
        verify(executor.mockConnection).prepareStatement("SELECT id, value1, value2, value3 FROM entities WHERE id = ?");
    }
}
