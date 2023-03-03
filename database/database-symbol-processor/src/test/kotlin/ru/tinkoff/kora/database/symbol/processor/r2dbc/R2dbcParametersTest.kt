package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
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

        repository.invoke<Any>("test", executor.con)

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

        repository.invoke<Any>("test", "test", 42)
        verify(executor.statement).bind(0, "test")
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
        executor.reset()

        repository.invoke<Any>("test", null, 42)
        verify(executor.statement).bindNull(0, String::class.java)
        verify(executor.statement).bind(1, 42)
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

        repository.invoke<Any>("test", "test", 42)
        verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        verify(executor.statement).bind(0, "test")
        verify(executor.statement).bind(1, 42)
        verify(executor.statement).execute()
        executor.reset()

        repository.invoke<Any>("test", null, 42)
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

        repository.invoke<Any>("test", new("SomeEntity", 42L, "test-value"))

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

        repository.invoke<Any>("test", 42L, "test-value")

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

        repository.invoke<Any>("test", new("TestEntity", 42L, null))
        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bindNull(1, String::class.java)
        executor.reset()

        repository.invoke<Any>("test", new("TestEntity", 42L, "test"))
        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bind(1, "test")
    }


    @Test
    fun testUnknownTypeParameter() {
        val mapper = Mockito.mock(R2dbcParameterColumnMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, value: UnknownType)
            }
            
            """.trimIndent(), """
            public class UnknownType {}
            
            """.trimIndent())

        repository.invoke<Any>("test", 42L, new("UnknownType"))

        verify(executor.statement).bind(0, 42L)
        verify(mapper).apply(ArgumentMatchers.same(executor.statement), ArgumentMatchers.eq(1), ArgumentMatchers.any())
    }

    @Test
    fun testUnknownTypeEntityField() {
        val mapper = Mockito.mock(R2dbcParameterColumnMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f)")
                fun test(id: Long, value0: TestEntity)
            }
            
            """.trimIndent(), """
            class UnknownType {}
            """.trimIndent(), """
            data class TestEntity (val f: UnknownType)
            """.trimIndent())

        repository.invoke<Any>("test", 42L, new("TestEntity", new("UnknownType")))

        verify(executor.statement).bind(0, 42L)
        verify(mapper).apply(ArgumentMatchers.same(executor.statement), ArgumentMatchers.eq(1), ArgumentMatchers.any())
    }

    @Test
    fun testNativeParameterNonFinalMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : R2dbcParameterColumnMapper<String> {
                override fun apply(stmt: Statement, index: Int, value0: String?) {
                    stmt.bind(index, mapOf("test" to value0))
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent())
        repository.invoke<Any>("test", 42L, "test-value")
        verify(executor.statement).bind(0, 42L)
        verify(executor.statement).bind(1, mapOf("test" to "test-value"))
    }

    @Test
    fun testMultipleParametersWithSameMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : R2dbcParameterColumnMapper<String> {
                override fun apply(stmt: Statement, index: Int, value0: String?) {
                    stmt.bind(index, mapOf("test" to value0))
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test1(id: Long, @Mapping(TestMapper::class) value0: String);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent())
    }

    @Test
    fun testMultipleParameterFieldsWithSameMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : R2dbcParameterColumnMapper<TestRecord> {
                override fun apply(stmt: Statement, index: Int, value0: TestRecord?) {
                    stmt.bind(index, mapOf("test" to value0.toString()))
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f1)")
                fun test1(id: Long, value0: TestRecord);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f1)")
                fun test2(id: Long, value0: TestRecord);
                @Query("INSERT INTO test(id, value1, value2) VALUES (:id, :value1.f1, :value2.f1)")
                fun test2(id: Long, value1: TestRecord, value2: TestRecord);
            }
            
            """.trimIndent(), """
            data class TestRecord(@Mapping(TestMapper::class) val f1: TestRecord, @Mapping(TestMapper::class) val f2: TestRecord){}
            
            """.trimIndent())
    }
}
