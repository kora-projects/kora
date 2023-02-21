package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.sqlclient.Tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper

class VertxParametersTest : AbstractVertxRepositoryTest() {
    @Test
    fun testConnectionParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                fun test(session: io.vertx.sqlclient.SqlClient)
            }
            """.trimIndent())

        repository.invoke<Any>("test", executor.connection)

        verify(executor.query).execute(Tuple.tuple().matcher(), any())
    }

    @Test
    fun testNativeParameter() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value1, :value2)")
                fun test(value1: String?, value2: Int)
            }
            
        """.trimIndent())

        repository.invoke<Any>("test", null, 1)
        verify(executor.connection).preparedQuery("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of(null, 1).matcher(), any())
        executor.reset()

        repository.invoke<Any>("test", "test", 1)
        verify(executor.connection).preparedQuery("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of("test", 1).matcher(), any())
    }


    @Test
    fun testUnknownTypeParameter() {
        val mapper = mock<VertxParameterColumnMapper<Any>>()
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: CustomType)
            }
            """.trimIndent(), "class CustomType{}")
        val value = new("CustomType")
        whenever(mapper.apply(any())).thenReturn("test")

        repository.invoke<Any>("test", value)

        verify(executor.connection).preparedQuery("INSERT INTO test(test) VALUES ($1)")
        verify(mapper).apply(same(value))
        verify(executor.query).execute(Tuple.of("test").matcher(), any())
    }

    @Test
    fun testParametersWithSimilarNames() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                fun test(value: String?, valueTest: Int)
            }
            """.trimIndent())

        repository.invoke<Any>("test", "test", 42)

        verify(executor.connection).preparedQuery("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of("test", 42).matcher(), any())
    }

    @Test
    fun testEntityFieldMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: VertxParameterColumnMapper<String?> {
                override fun apply(value: String?): Any? {
                    return mapOf("test" to value)
                }
            }
            """.trimIndent(), """
            public data class SomeEntity(val id: Long, @Mapping(StringToJsonbParameterMapper::class) val value: String)
            """.trimIndent(), """
            @Repository
            interface TestRepository: VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
                fun test(entity: SomeEntity)
            }

            """.trimIndent())

        repository.invoke<Any>("test", new("SomeEntity", 42L, "test-value"))

        verify(executor.connection).preparedQuery("INSERT INTO test(id, value) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of(42L, mapOf("test" to "test-value")).matcher(), any())
    }

    @Test
    fun testNativeParameterWithMapping() {
        val repository = compile(listOf<Any>(), """
            class StringToJsonbParameterMapper: VertxParameterColumnMapper<String?> {
                override fun apply(value: String?): Any? {
                    return mapOf("test" to value)
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository: VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, @Mapping(StringToJsonbParameterMapper::class) value: String)
            }
            """.trimIndent())

        repository.invoke<Any>("test", 42L, "test-value")

        verify(executor.connection).preparedQuery("INSERT INTO test(id, value) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of(42L, mapOf("test" to "test-value")).matcher(), any())
    }

    @Test
    fun testDataClassParameter() {
        val repository = compile(listOf<Any>(), """
        @Repository
        interface TestRepository: VertxRepository {
            @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
            fun test(entity: TestEntity)
        }
        """.trimIndent(), """
        data class TestEntity(val id: Long, val value: String?)    
        """.trimIndent())

        repository.invoke<Any>("test", new("TestEntity", 42, null))
        verify(executor.connection).preparedQuery("INSERT INTO test(id, value) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of(42L, null).matcher(), any())
        executor.reset()

        repository.invoke<Any>("test", new("TestEntity", 42, "test"))
        verify(executor.connection).preparedQuery("INSERT INTO test(id, value) VALUES ($1, $2)")
        verify(executor.query).execute(Tuple.of(42L, "test").matcher(), any())
    }
}
