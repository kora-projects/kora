package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.sqlclient.Tuple
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
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


    @Test
    fun testUnknownTypeParameter() {
        val mapper = Mockito.mock(VertxParameterColumnMapper::class.java) as VertxParameterColumnMapper<Any>
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, value: UnknownType)
            }
            
            """.trimIndent(), """
            public class UnknownType {}
            
            """.trimIndent())
        val o = new("UnknownType")

        repository.invoke<Any>("test", 42L, o)

        verify(mapper).apply(same(o))
    }

    @Test
    fun testUnknownTypeEntityField() {
        val mapper = Mockito.mock(VertxParameterColumnMapper::class.java) as VertxParameterColumnMapper<Any>
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f)")
                fun test(id: Long, value0: TestEntity)
            }
            
            """.trimIndent(), """
            class UnknownType {}
            """.trimIndent(), """
            data class TestEntity (val f: UnknownType)
            """.trimIndent())
        val o = new("UnknownType")

        repository.invoke<Any>("test", 42L, new("TestEntity", o))

        verify(mapper).apply(same(o))
    }

    @Test
    fun testNativeParameterNonFinalMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : VertxParameterColumnMapper<String> {
                override fun apply(value0: String?) =  mapOf("test" to value0)
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent())

        repository.invoke<Any>("test", 42L, "test-value")

        verify(executor.query).execute(Tuple.of(42L, mapOf("test" to "test-value")).matcher(), any())
    }

    @Test
    fun testMultipleParametersWithSameMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : VertxParameterColumnMapper<String> {
                override fun apply(value0: String?) =  mapOf("test" to value0)
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : VertxRepository {
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
            open class TestMapper : VertxParameterColumnMapper<TestRecord> {
                override fun apply(value0: TestRecord?) =  mapOf("test" to value0.toString())
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : VertxRepository {
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
