package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.oss.driver.api.core.cql.Statement
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper

class CassandraParametersTest : AbstractCassandraRepositoryTest() {

    @Test
    fun testConnectionParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                fun test(session: com.datastax.oss.driver.api.core.CqlSession)
            }
            """.trimIndent())

        repository.invoke<Any>("test", executor.mockSession)

        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testNativeParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: Int)
            }
            """.trimIndent())

        repository.invoke<Any>("test", 42)

        verify(executor.boundStatementBuilder).setInt(0, 42)
        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testUnknownTypeParameter() {
        val mapper = mock<CassandraParameterColumnMapper<Any>>()
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: CustomType)
            }
            """.trimIndent(), "class CustomType{}")
        val value = new("CustomType")

        repository.invoke<Any>("test", value)

        verify(mapper).apply(same(executor.boundStatementBuilder), eq(0), same(value))
        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testParametersWithSimilarNames() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                fun test(value: String?, valueTest: Int)
            }
            """.trimIndent())

        repository.invoke<Any>("test", "test", 42)

        verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)")
        verify(executor.boundStatementBuilder).setString(0, "test")
        verify(executor.boundStatementBuilder).setInt(1, 42)
        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testEntityFieldMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: CassandraParameterColumnMapper<String?> {
                override fun apply(stmt: SettableByName<*>, index: Int, value: String?) {
                    stmt.set(index, mapOf("test" to value), Map::class.java)
                }
            }
            """.trimIndent(), """
            public data class SomeEntity(val id: Long, @Mapping(StringToJsonbParameterMapper::class) val value: String)
            """.trimIndent(), """
            @Repository
            interface TestRepository: CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                fun test(entity: SomeEntity)
            }

            """.trimIndent())

        repository.invoke<Any>("test", new("SomeEntity", 42L, "test-value"))

        verify(executor.boundStatementBuilder).setLong(0, 42L)
        verify(executor.boundStatementBuilder).set(1, mapOf("test" to "test-value"), Map::class.java)
        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testNativeParameterWithMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: CassandraParameterColumnMapper<String?> {
                override fun apply(stmt: SettableByName<*>, index: Int, value: String?) {
                    stmt.set(index, mapOf("test" to value), Map::class.java)
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository: CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, @Mapping(StringToJsonbParameterMapper::class) value: String)
            }
            """.trimIndent())

        repository.invoke<Any>("test", 42L, "test-value")

        verify(executor.boundStatementBuilder).setLong(0, 42L)
        verify(executor.boundStatementBuilder).set(1, mapOf("test" to "test-value"), Map::class.java)
        verify(executor.mockSession).execute(any<Statement<*>>())
    }

    @Test
    fun testDataClassParameter() {
        val repository = compile(listOf<Any>(), """
        @Repository
        interface TestRepository: CassandraRepository {
            @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
            fun test(entity: TestEntity)
        }
        """.trimIndent(), """
        data class TestEntity(val id: Long, val value: String?)    
        """.trimIndent())

        repository.invoke<Any>("test", new("TestEntity", 42, null))
        verify(executor.boundStatementBuilder).setLong(0, 42)
        verify(executor.boundStatementBuilder).setToNull(1)
        executor.reset()

        repository.invoke<Any>("test", new("TestEntity", 42, "test"))
        verify(executor.boundStatementBuilder).setLong(0, 42)
        verify(executor.boundStatementBuilder).setString(1, "test")
    }
}
