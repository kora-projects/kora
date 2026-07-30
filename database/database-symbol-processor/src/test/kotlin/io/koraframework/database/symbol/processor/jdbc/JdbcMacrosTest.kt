package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime

class JdbcMacrosTest : AbstractJdbcRepositoryTest() {

    @Test
    fun returnTable() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                        
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
                        
                @Query("SELECT * FROM %{return#table} WHERE id = :id")
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
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                        
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
                        
                @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?)")
    }

    @Test
    fun insertBatch() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts}")
                fun insert(@Batch entity: List<Entity>): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )

        Mockito.`when`(executor.preparedStatement.executeLargeBatch()).thenReturn(longArrayOf(1L))
        repository.invoke<Any>("insert", listOf(newGenerated("Entity", "1", 1, "1", "1").invoke()))
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(value1, value2, value3) VALUES (?, ?, ?)")
    }

    @Test
    fun columnsAndValuesWithoutId() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Query("INSERT INTO %{entity#table}(%{entity#columns -= @id}) VALUES (%{entity#values -= @id})")
                fun insert(entity: Entity): UpdateCount
            }

            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String,
                                  @field:Column("value1") val field1: Long,
                                  val value2: String,
                                  val value3: String?)
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
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
                fun upsert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("upsert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("INSERT INTO entities(id, value1, value2, value3) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET value1 = ?, value2 = ?, value3 = ?")
    }

    @Test
    fun upsertBatch() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
                fun upsert(@Batch entity: List<Entity>): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )

        Mockito.`when`(executor.preparedStatement.executeLargeBatch()).thenReturn(longArrayOf(1L))
        repository.invoke<Any>("upsert", listOf(newGenerated("Entity", "1", 1, "1", "1").invoke()))
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id = ?")
    }

    @Test
    fun entityTableAndUpdateBatch() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(@Batch entity: List<Entity>): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent()
        )

        Mockito.`when`(executor.preparedStatement.executeLargeBatch()).thenReturn(longArrayOf(1L))
        repository.invoke<Any>("insert", listOf(newGenerated("Entity", "1", 1, "1", "1").invoke()))
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
                data class Entity(@field:Id @field:Embedded val id: EntityId, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
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
    fun entityTableAndUpdateWhereIdIsEmbeddedNullable() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id @field:Embedded val id: EntityId?, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
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
    fun entityTableAndUpdateWhereIdIsEmbeddedNullableParam() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
                @Table("entities")
                data class Entity(@field:Id @field:Embedded val id: EntityId, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
            """.trimIndent(), """
                data class EntityId(val id1: String, val id2: String?)
            
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
    fun entityTableAndUpdateWhereIdIsEmbeddedNullableParamNullable() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            """.trimIndent(), """
            @Table("entities")
            data class Entity(@field:Id @field:Embedded val id: EntityId?, 
                              @field:Column("value1") val field1: Long, 
                              val value2: String, 
                              val value3: String?)
            """.trimIndent(), """
            data class EntityId(val id1: String, val id2: String?)
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", "2").invoke(), 1, "1", "1").invoke()
        )
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?")
    }

    class TimeJdbcResultColumnMapper : JdbcResultColumnMapper<OffsetDateTime> {
        override fun apply(row: ResultSet, index: Int): OffsetDateTime = row.getObject(index, OffsetDateTime::class.java)
    }

    class TimeJdbcParameterColumnMapper : JdbcParameterColumnMapper<OffsetDateTime> {
        override fun set(stmt: PreparedStatement, index: Int, value: OffsetDateTime?) = stmt.setObject(index, value)
    }

    @Test
    fun entityTableAndUpdateWhereIdIsEmbeddedWithMapper() {
        val repository = compile(
            listOf<Any>(TimeJdbcParameterColumnMapper()), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
            @Table("entities")
            data class Entity(@field:Id @field:Embedded val id: EntityId, 
                              @field:Column("value1") val field1: Long, 
                              val value2: String, 
                              val value3: String?)
            """.trimIndent(), """
            data class EntityId(val id1: String, val id2: java.time.OffsetDateTime)
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", OffsetDateTime.MIN).invoke(), 1, "1", "1").invoke()
        )
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?")
    }

    @Test
    fun entityTableAndUpdateWhereIdIsEmbeddedNullableWithMapper() {
        val repository = compile(
            listOf<Any>(TimeJdbcParameterColumnMapper()), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
            @Table("entities")
            data class Entity(@field:Id @field:Embedded val id: EntityId?, 
                              @field:Column("value1") val field1: Long, 
                              val value2: String, 
                              val value3: String?)
            """.trimIndent(), """
            data class EntityId(val id1: String, val id2: java.time.OffsetDateTime)
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", OffsetDateTime.MIN).invoke(), 1, "1", "1").invoke()
        )
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?")
    }

    @Test
    fun entityTableAndUpdateWhereIdIsEmbeddedNullableParamWithMapper() {
        val repository = compile(
            listOf<Any>(TimeJdbcParameterColumnMapper()), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
            @Table("entities")
            data class Entity(@field:Id @field:Embedded val id: EntityId, 
                              @field:Column("value1") val field1: Long, 
                              val value2: String, 
                              val value3: String?)
            """.trimIndent(), """
            data class EntityId(val id1: String, val id2: java.time.OffsetDateTime?)
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", OffsetDateTime.MIN).invoke(), 1, "1", "1").invoke()
        )
        Mockito.verify(executor.mockConnection)
            .prepareStatement("UPDATE entities SET value1 = ?, value2 = ?, value3 = ? WHERE id1 = ? AND id2 = ?")
    }

    @Test
    fun entityTableAndUpdateWhereIdIsEmbeddedNullableParamNullableWithMapper() {
        val repository = compile(
            listOf<Any>(TimeJdbcParameterColumnMapper()), """
            @Repository
            interface TestRepository : JdbcRepository {
                            
                @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
                fun insert(entity: Entity): UpdateCount
            }
            
            """.trimIndent(), """
            @Table("entities")
            data class Entity(@field:Id @field:Embedded val id: EntityId?, 
                              @field:Column("value1") val field1: Long, 
                              val value2: String, 
                              val value3: String?)
            """.trimIndent(), """
            data class EntityId(val id1: String, val id2: java.time.OffsetDateTime?)
            """.trimIndent()
        )
        repository.invoke<Any>(
            "insert",
            newGenerated("Entity", newGenerated("EntityId", "1", OffsetDateTime.MIN).invoke(), 1, "1", "1").invoke()
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            
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
                data class Entity(@field:Id val id: String, 
                                  @field:Column("value1") val field1: Long, 
                                  val value2: String, 
                                  val value3: String?)
            """.trimIndent()
        )
        repository.invoke<Any>("insert", newGenerated("Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection).prepareStatement("UPDATE entities SET value1 = ? WHERE id = ?")
    }

    @Test
    fun returnEmbeddedSelectsWithTableAliases() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Table("users")
                data class User(@field:Id val id: String, val name: String)

                @Table("orders")
                data class Order(@field:Id val id: String, @field:Column("user_id") val userId: String, val number: String)

                data class UserOrderView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val order: Order)

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                fun find(id: String): UserOrderView?
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.UserOrderView?> {
                override fun apply(rs: ResultSet): TestRepository.UserOrderView? {
                  return null
                }
            }
            """.trimIndent()
        )
        repository.invoke<Any>("find", "1")
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u JOIN orders o ON o.user_id = u.id WHERE u.id = ?")
    }

    @Test
    fun nestedReturnTargetSelectsWithTableAliases() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Table("users")
                data class User(@field:Id val id: String, val name: String)

                @Table("orders")
                data class Order(@field:Id val id: String, @field:Column("user_id") val userId: String, val number: String)

                data class UserOrderView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val order: Order)

                @Query("SELECT %{return.user#selects}, %{return.order#selects} FROM %{return.user#table as u} JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                fun find(id: String): UserOrderView?
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.UserOrderView?> {
                override fun apply(rs: ResultSet): TestRepository.UserOrderView? {
                  return null
                }
            }
            """.trimIndent()
        )
        repository.invoke<Any>("find", "1")
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u JOIN orders o ON o.user_id = u.id WHERE u.id = ?")
    }

    @Test
    fun entityWhereIdWithTableAlias() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Table("entities")
                data class Entity(@field:Id val id: String,
                                  @field:Column("value1") val field1: Long,
                                  val value2: String,
                                  val value3: String?)

                @Query("SELECT %{return#selects} FROM %{entity#table as e} WHERE %{entity#where = @id}")
                fun find(entity: Entity): Entity?
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.Entity?> {
                override fun apply(rs: ResultSet): TestRepository.Entity? {
                  return null
                }
            }
            """.trimIndent()
        )
        repository.invoke<Any>("find", newGenerated("TestRepository\$Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT id, value1, value2, value3 FROM entities e WHERE e.id = ?")
    }

    @Test
    fun leftJoinNullableEmbeddedEntity() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} LEFT JOIN %{return.order#table as o} ON o.user_id = u.id WHERE u.id = :id")
                fun find(id: String): UserOrderView?
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<UserOrderView?> {
                override fun apply(rs: ResultSet): UserOrderView? {
                    return UserOrderView(User(rs.getString(1), rs.getString(2)), null)
                }
            }
            """.trimIndent(), """
            @Table("users")
            data class User(@field:Id val id: String, val name: String)
            """.trimIndent(), """
            @Table("orders")
            data class Order(@field:Id val id: String, @field:Column("user_id") val userId: String, val number: String)
            """.trimIndent(), """
            data class UserOrderView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val order: Order?)
            """.trimIndent()
        )

        Mockito.`when`(executor.resultSet.next()).thenReturn(true, false)
        Mockito.`when`(executor.resultSet.findColumn("u_id")).thenReturn(1)
        Mockito.`when`(executor.resultSet.findColumn("u_name")).thenReturn(2)
        Mockito.`when`(executor.resultSet.findColumn("o_id")).thenReturn(3)
        Mockito.`when`(executor.resultSet.findColumn("o_user_id")).thenReturn(4)
        Mockito.`when`(executor.resultSet.findColumn("o_number")).thenReturn(5)
        Mockito.`when`(executor.resultSet.getString(1)).thenReturn("u1")
        Mockito.`when`(executor.resultSet.getString(2)).thenReturn("User 1")
        Mockito.`when`(executor.resultSet.getString(3)).thenReturn(null)
        Mockito.`when`(executor.resultSet.getString(4)).thenReturn(null)
        Mockito.`when`(executor.resultSet.getString(5)).thenReturn(null)
        Mockito.`when`(executor.resultSet.wasNull()).thenReturn(false, false, true, true, true)

        val result = repository.invoke<Any>("find", "u1")

        assertThat(result).isNotNull()
        assertThat(result!!.javaClass.getMethod("getOrder").invoke(result)).isNull()
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u LEFT JOIN orders o ON o.user_id = u.id WHERE u.id = ?")
    }

    @Test
    fun oneToManyEmbeddedCollectionMapping() {
        val repository = compile(
            listOf(newGenerated("TestResultSetMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return.user#table as u} LEFT JOIN %{return.orders#table as o} ON o.user_id = u.id")
                fun find(): List<UserOrdersView>
            }
            """.trimIndent(), """
            class TestResultSetMapper : JdbcResultSetMapper<List<UserOrdersView>> {
                override fun apply(rs: ResultSet): List<UserOrdersView> {
                    return listOf(UserOrdersView(User("u1", "User 1"), listOf(Order("o1", "u1", "n1"), Order("o2", "u1", "n2"))))
                }
            }
            """.trimIndent(), """
            @Table("users")
            data class User(@field:Id val id: String, val name: String)
            """.trimIndent(), """
            @Table("orders")
            data class Order(@field:Id val id: String, @field:Column("user_id") val userId: String, val number: String)
            """.trimIndent(), """
            data class UserOrdersView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val orders: List<Order>)
            """.trimIndent()
        )

        Mockito.`when`(executor.resultSet.next()).thenReturn(true, true, false)
        Mockito.`when`(executor.resultSet.findColumn("u_id")).thenReturn(1)
        Mockito.`when`(executor.resultSet.findColumn("u_name")).thenReturn(2)
        Mockito.`when`(executor.resultSet.findColumn("o_id")).thenReturn(3)
        Mockito.`when`(executor.resultSet.findColumn("o_user_id")).thenReturn(4)
        Mockito.`when`(executor.resultSet.findColumn("o_number")).thenReturn(5)
        Mockito.`when`(executor.resultSet.getString(1)).thenReturn("u1", "u1")
        Mockito.`when`(executor.resultSet.getString(2)).thenReturn("User 1", "User 1")
        Mockito.`when`(executor.resultSet.getString(3)).thenReturn("o1", "o2")
        Mockito.`when`(executor.resultSet.getString(4)).thenReturn("u1", "u1")
        Mockito.`when`(executor.resultSet.getString(5)).thenReturn("n1", "n2")
        Mockito.`when`(executor.resultSet.wasNull()).thenReturn(false, false, false, false, false, false, false, false, false, false)

        val result = repository.invoke<List<*>>("find")

        assertThat(result!!).hasSize(1)
        val orders = result[0]!!.javaClass.getMethod("getOrders").invoke(result[0]) as List<*>
        assertThat(orders).hasSize(2)
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT u.id AS u_id, u.name AS u_name, o.id AS o_id, o.user_id AS o_user_id, o.number AS o_number FROM users u LEFT JOIN orders o ON o.user_id = u.id")
    }

    @Test
    fun typeUseColumnArgumentWhere() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            interface AbstractJdbcRepository<K, V> : JdbcRepository {

                @Query("SELECT %{return#selects} FROM %{return#table} WHERE %{keyArg#where}")
                fun findById(keyArg: K): V?
            }

            @Repository
            interface TestRepository : AbstractJdbcRepository<@Column("id") String, TestRepository.Entity> {

                @Table("entities")
                data class Entity(@field:Id val id: String,
                                  @field:Column("value1") val field1: Long,
                                  val value2: String,
                                  val value3: String?)
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
    fun genericTypeArgumentSelectsAndTable() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            interface AbstractJdbcRepository<V> : JdbcRepository {

                @Query("SELECT %{V#selects} FROM %{V#table}")
                fun findOne(): V?
            }

            @Repository
            interface TestRepository : AbstractJdbcRepository<TestRepository.Entity> {

                @Table("entities")
                data class Entity(@field:Id val id: String,
                                  @field:Column("value1") val field1: Long,
                                  val value2: String,
                                  val value3: String?)
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.Entity?> {
                override fun apply(rs: ResultSet): TestRepository.Entity? {
                  return null
                }
            }
            """.trimIndent()
        )
        repository.invoke<Any>("findOne")
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT id, value1, value2, value3 FROM entities")
    }

    @Test
    fun genericTypeArgumentWhereId() {
        val repository = compile(
            listOf(newGenerated("TestRowMapper")), """
            interface AbstractJdbcRepository<V> : JdbcRepository {

                @Query("SELECT %{V#selects} FROM %{V#table} WHERE %{V#where = @id}")
                fun findByEntity(entity: V): V?
            }

            @Repository
            interface TestRepository : AbstractJdbcRepository<TestRepository.Entity> {

                @Table("entities")
                data class Entity(@field:Id val id: String,
                                  @field:Column("value1") val field1: Long,
                                  val value2: String,
                                  val value3: String?)
            }
            """.trimIndent(), """
            class TestRowMapper : JdbcResultSetMapper<TestRepository.Entity?> {
                override fun apply(rs: ResultSet): TestRepository.Entity? {
                  return null
                }
            }
            """.trimIndent()
        )
        repository.invoke<Any>("findByEntity", newGenerated("TestRepository\$Entity", "1", 1, "1", "1").invoke())
        Mockito.verify(executor.mockConnection)
            .prepareStatement("SELECT id, value1, value2, value3 FROM entities WHERE id = ?")
    }
}
