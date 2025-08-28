package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

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
                record EntityId(String id1, @Nullable java.time.OffsetDateTime id2) {}
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
                record EntityId(String id1, @Nullable java.time.OffsetDateTime id2) {}
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
}
