package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper

class R2dbcParametersTest : AbstractR2dbcTest() {
    @Test
    fun testConnectionParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                fun test(connection: io.r2dbc.spi.Connection)
            }
            """.trimIndent())

        repository.invoke("test", executor.con)

        verify(executor.statement).execute()
    }

    @Test
    fun testNativeParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
                fun test(value1: String?, value2: Int)
            }
        """.trimIndent())

        repository.invoke("test", "test", 42)
        verify(executor.statement).bind(0, "test")
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
        executor.reset()

        repository.invoke("test", null, 42)
        verify(executor.statement).bindNull(0, String::class.java)
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
    }

    @Test
    fun testUnknownTypeParameter() {
        val mapper = mock<R2dbcParameterColumnMapper<Any>>()
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: CustomType)
            }
            """.trimIndent(), "class CustomType{}")
        val value = new("CustomType")

        repository.invoke("test", value)

        verify(mapper).apply(same(executor.statement), eq(0), same(value))
        verify(executor.statement).execute()
    }

    @Test
    fun testParametersWithSimilarNames() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                fun test(value: String?, valueTest: Int)
            }
            """.trimIndent())

        repository.invoke("test", "test", 42)
        verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.statement).bind(0, "test")
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
        executor.reset()

        repository.invoke("test", null, 42)
        verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.statement).bindNull(0, String::class.java)
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
        executor.reset()
    }

    @Test
    fun testEntityFieldMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: R2dbcParameterColumnMapper<String?> {
                override fun apply(stmt: Statement, index: Int, value: String?) {
                    stmt.bind(index, mapOf("test" to value))
                }
            }
            """.trimIndent(), """
            public data class SomeEntity(val id: Long, @Mapping(StringToJsonbParameterMapper::class) val value: String)
            """.trimIndent(), """
            @Repository
            interface TestRepository: R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                fun test(entity: SomeEntity)
            }

            """.trimIndent())

        repository.invoke("test", new("SomeEntity", 42L, "test-value"))

        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bind(1, mapOf("test" to "test-value"))
        verify(executor.statement).execute()
    }

    @Test
    fun testNativeParameterWithMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: R2dbcParameterColumnMapper<String?> {
                override fun apply(stmt: Statement, index: Int, value: String?) {
                    stmt.bind(index, mapOf("test" to value))
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository: R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, @Mapping(StringToJsonbParameterMapper::class) value: String)
            }
            """.trimIndent())

        repository.invoke("test", 42L, "test-value")

        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bind(1, mapOf("test" to "test-value"))
        verify(executor.statement).execute()
    }

    @Test
    fun testDataClassParameter() {
        val repository = compile(listOf<Any>(), """
        @Repository
        interface TestRepository: R2dbcRepository {
            @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
            fun test(entity: TestEntity)
        }
        """.trimIndent(), """
        data class TestEntity(val id: Long, val value: String?)    
        """.trimIndent())

        repository.invoke("test", new("TestEntity", 42L, null))
        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bindNull(1, String::class.java)
        executor.reset()

        repository.invoke("test", new("TestEntity", 42L, "test"))
        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bind(1, "test")
    }
}
