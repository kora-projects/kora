package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.verify
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.jvmErasure

class JdbcParametersTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testConnectionParameter() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES ('test')")
                fun test(connection: Connection)
            }
            """.trimIndent()
        )

        repository.invoke<Any>("test", executor.mockConnection)

        verify(executor.preparedStatement).execute()
        verify(executor.preparedStatement).updateCount
    }

    @Test
    fun testNativeParameter() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(value: Int)
            }
            """.trimIndent()
        )

        repository.invoke<Any>("test", 42)

        verify(executor.preparedStatement).setInt(1, 42)
        verify(executor.preparedStatement).updateCount
    }

    @Test
    fun testParametersWithSimilarNames() {
        val repository = compile(
            listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value1, value2) VALUES (:value, :valueTest)")
                fun test(value: String?, valueTest: Int)
            }
            """.trimIndent()
        )

        repository.invoke<Any>("test", "test", 42)

        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value1, value2) VALUES (?, ?)")
        verify(executor.preparedStatement).setString(1, "test")
        verify(executor.preparedStatement).setInt(2, 42)
    }

    @Test
    fun testEntityFieldMapping() {
        val repository = compile(
            listOf<Any>(), """
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

            """.trimIndent()
        )

        repository.invoke<Any>("test", new("SomeEntity", 42L, "test-value"))

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(executor.preparedStatement).setObject(2, mapOf("test" to "test-value"))
    }

    @Test
    fun testNativeParameterWithMapping() {
        val repository = compile(
            listOf<Any>(), """
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
            """.trimIndent()
        )

        repository.invoke<Any>("test", 42L, "test-value")

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(executor.preparedStatement).setObject(2, mapOf("test" to "test-value"))
    }

    @Test
    fun testUnknownTypeParameter() {
        val mapper = Mockito.mock(JdbcParameterColumnMapper::class.java)
        val repository = compile(
            listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, value: UnknownType)
            }
            
            """.trimIndent(), """
            class UnknownType {}
            
            """.trimIndent()
        )

        repository.invoke<Any>("test", 42L, new("UnknownType"))

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(mapper).set(ArgumentMatchers.same(executor.preparedStatement), ArgumentMatchers.eq(2), ArgumentMatchers.any())
    }

    @Test
    fun testUnknownTypeEntityField() {
        val mapper = Mockito.mock(JdbcParameterColumnMapper::class.java)
        val repository = compile(
            listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f)")
                fun test(id: Long, value0: TestEntity)
            }
            
            """.trimIndent(), """
            class UnknownType {}
            """.trimIndent(), """
            data class TestEntity (val f: UnknownType)
            """.trimIndent()
        )

        repository.invoke<Any>("test", 42L, new("TestEntity", new("UnknownType")))

        verify(executor.preparedStatement).setLong(1, 42L)
        verify(mapper).set(ArgumentMatchers.same(executor.preparedStatement), ArgumentMatchers.eq(2), ArgumentMatchers.any())
    }

    @Test
    fun testNativeParameterNonFinalMapper() {
        val repository = compile(
            listOf(newGenerated("TestMapper")), """
            open class TestMapper : JdbcParameterColumnMapper<String> {
                override fun set(stmt: PreparedStatement, index: Int, value0: String?) {
                    stmt.setObject(index, mapOf("test" to value0))
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent()
        )
        repository.invoke<Any>("test", 42L, "test-value")
        Mockito.verify(executor.preparedStatement).setLong(1, 42L)
        Mockito.verify(executor.preparedStatement).setObject(2, mapOf("test" to "test-value"))
    }

    @Test
    fun testMultipleParametersWithSameMapper() {
        val repository = compile(
            listOf(newGenerated("TestMapper")), """
            open class TestMapper : JdbcParameterColumnMapper<String> {
                override fun set(stmt: PreparedStatement, index: Int, value0: String?) {
                    stmt.setObject(index, mapOf("test" to value0))
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test1(id: Long, @Mapping(TestMapper::class) value0: String);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleParameterFieldsWithSameMapper() {
        val repository = compile(
            listOf(newGenerated("TestMapper")), """
            open class TestMapper : JdbcParameterColumnMapper<TestRecord> {
                override fun set(stmt: PreparedStatement, index: Int, value0: TestRecord?) {
                    stmt.setObject(index, mapOf("test" to value0.toString()))
                }
            }
            """.trimIndent(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f1)")
                fun test1(id: Long, value0: TestRecord);
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f1)")
                fun test2(id: Long, value0: TestRecord);
                @Query("INSERT INTO test(id, value1, value2) VALUES (:id, :value1.f1, :value2.f1)")
                fun test2(id: Long, value1: TestRecord, value2: TestRecord);
            }
            
            """.trimIndent(), """
            data class TestRecord(@Mapping(TestMapper::class) val f1: TestRecord, @Mapping(TestMapper::class) val f2: TestRecord){}
            
            """.trimIndent()
        )
    }

    @Test
    fun testTagOnParameter() {
        val mapper = Mockito.mock(JdbcParameterColumnMapper::class.java) as JdbcParameterColumnMapper<Int>
        val repository = compile(
            listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(test) VALUES (:value)")
                fun test(@Tag(TestRepository::class) value: Int)
            }
            """.trimIndent()
        )

        repository.invoke<Any>("test", 42)

        verify(mapper).set(ArgumentMatchers.same(executor.preparedStatement), ArgumentMatchers.eq(1), ArgumentMatchers.eq(42))

        val mapperConstructorParameter = repository.repositoryClass.constructors.first().parameters[1]
        Assertions.assertThat(mapperConstructorParameter.type.jvmErasure).isEqualTo(JdbcParameterColumnMapper::class)
        val tag = mapperConstructorParameter.findAnnotations(Tag::class).first()
        Assertions.assertThat(tag).isNotNull()
        Assertions.assertThat(tag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("TestRepository")))
    }

    @Test
    fun testTagOnEntityField() {
        val mapper = Mockito.mock(JdbcParameterColumnMapper::class.java) as JdbcParameterColumnMapper<String>
        val repository = compile(
            listOf(mapper), """
        @Repository
        interface TestRepository: JdbcRepository {
            @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
            fun test(entity: TestEntity)
        }
        """.trimIndent(), """
        data class TestEntity(val id: Long, @Tag(TestRepository::class) val value: String?)    
        """.trimIndent()
        )

        repository.invoke<Any>("test", new("TestEntity", 42, "test-value"))
        verify(executor.preparedStatement).setLong(1, 42)
        verify(mapper).set(ArgumentMatchers.same(executor.preparedStatement), ArgumentMatchers.eq(2), ArgumentMatchers.eq("test-value"))

        val mapperConstructorParameter = repository.repositoryClass.constructors.first().parameters[1]
        Assertions.assertThat(mapperConstructorParameter.type.jvmErasure).isEqualTo(JdbcParameterColumnMapper::class)
        val tag = mapperConstructorParameter.findAnnotations(Tag::class).first()
        Assertions.assertThat(tag).isNotNull()
        Assertions.assertThat(tag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("TestRepository")))
    }
}
