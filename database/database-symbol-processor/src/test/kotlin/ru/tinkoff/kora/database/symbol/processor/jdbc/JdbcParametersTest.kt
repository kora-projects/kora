package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper

class JdbcParametersTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testConnectionParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                fun test(connection: Connection)
            }
            """.trimIndent())

        repository.invoke("test", executor.mockConnection)

        verify(executor.preparedStatement).execute()
        verify(executor.preparedStatement).updateCount
    }

    @Test
    fun testNativeParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: Int)
            }
            """.trimIndent())

        repository.invoke("test", 42)

        verify(executor.preparedStatement).setInt(1, 42)
        verify(executor.preparedStatement).updateCount
    }

    @Test
    fun testUnknownTypeParameter() {
        val mapper = mock<JdbcParameterColumnMapper<Any>>()
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: CustomType)
            }
            """.trimIndent(), "class CustomType{}")
        val value = new("CustomType")

        repository.invoke("test", value)

        verify(mapper).set(same(executor.preparedStatement), eq(1), same(value))
        verify(executor.preparedStatement).updateCount
    }

    @Test
    fun testParametersWithSimilarNames() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                fun test(value: String?, valueTest: Int)
            }
            """.trimIndent())

        repository.invoke("test", "test", 42)

        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value1, value2) VALUES (?, ?)")
        verify(executor.preparedStatement).setString(1, "test")
        verify(executor.preparedStatement).setInt(2, 42)
    }

    @Test
    fun testEntityFieldMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: JdbcParameterColumnMapper<String?> {
                override fun set(stmt: PreparedStatement, index: Int, value: String?) {
                    stmt.setObject(index, mapOf("test" to value))
                }
            }
            """.trimIndent(), """
            public data class SomeEntity(val id: Long, @Mapping(StringToJsonbParameterMapper::class) val value: String)
            """.trimIndent(), """
            @Repository
            interface TestRepository: JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                fun test(entity: SomeEntity)
            }

            """.trimIndent())

        repository.invoke("test", new("SomeEntity", 42L, "test-value"))

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(executor.preparedStatement).setObject(2, mapOf("test" to "test-value"))
    }

    @Test
    fun testNativeParameterWithMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: JdbcParameterColumnMapper<String?> {
                override fun set(stmt: PreparedStatement, index: Int, value: String?) {
                    stmt.setObject(index, mapOf("test" to value))
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository: JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, @Mapping(StringToJsonbParameterMapper::class) value: String);
            }
            """.trimIndent())

        repository.invoke("test", 42L, "test-value")

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(executor.preparedStatement).setObject(2, mapOf("test" to "test-value"))
    }
}
