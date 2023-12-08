package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.concurrent.Executors

class JdbcMacrosTest : AbstractJdbcRepositoryTest() {

    @Test
    fun returnTable() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                        
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
                        
                @Query("SELECT * FROM %{return#table} WHERE id = :id")
                @Nullable
                fun findById(id: String): Entity?
            }
            
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.Entity?> {
                override fun apply(rs: ResultSet): TestRepository.Entity? {
                  return null
                }
            }
            
            """.trimIndent()
        )
        repository.invoke<Any>("findById", "1")
        Mockito.verify(executor.mockConnection).prepareStatement("SELECT * FROM entities WHERE id = ?")
    }

    @Test
    fun returnSelectsAndTable() {
        val repository = compile(
            listOf(Executors.newSingleThreadExecutor(), newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                        
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
                        
                @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
                suspend fun findById(id: String): Entity?
            }
            
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.Entity?> {
                override fun apply(rs: ResultSet): TestRepository.Entity? {
                  return null
                }
            }
            
            """.trimIndent()
        )
        repository.invoke<Any>("findById", "1")
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT id, value1, value2, value3 FROM entities WHERE id = ?")
    }

    @Test
    fun inserts() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?)")
    }

    @Test
    fun insertsWithoutId() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts -= @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)")
    }

    @Test
    fun insertsExtended() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : ParentRepository<Entity> {
                            
            }
            
            """.trimIndent(), """
            interface ParentRepository<T> : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts -= @id}")
                fun insert(entity: T): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)")
    }

    @Test
    fun insertsWithoutField() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts -= field1}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(id, value2, value3) VALUES (?, ?, ?)")
    }

    @Test
    fun upsert() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET value1 = ?, value2 = ?, value3 = ?")
    }

    @Test
    fun entityTableAndUpdate() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id = ?")
    }

    @Test
    fun entityTableAndUpdateWhereIdIsEmbedded() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id @field:Embedded val id: EntityId, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent(), """
                data class EntityId(val id1: String, val id2: String)
            
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", "2").invoke(), 1, "1", "1").invoke()
        )
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?")
    }

    @Test
    fun entityTableAndUpdateExclude() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates -= field1} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value2 = ?, value3 = ? WHERE id = ?")
    }

    @Test
    fun entityTableAndUpdateInclude() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates = field1} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, @field:Column("value1") val field1: Long, val value2: String, val value3: String?)
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ? WHERE id = ?")
    }
}
