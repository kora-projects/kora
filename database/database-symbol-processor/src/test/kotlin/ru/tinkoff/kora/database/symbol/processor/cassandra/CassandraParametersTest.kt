package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.core.cql.ColumnDefinition
import com.datastax.oss.driver.api.core.cql.Statement
import com.datastax.oss.driver.api.core.type.codec.TypeCodec
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry
import com.datastax.oss.driver.internal.core.cql.DefaultColumnDefinitions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper
import java.util.List

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

    @Test
    fun testBatchDataClassParameter() {
        val repository = compile(listOf<Any>(), """
        @Repository
        interface TestRepository: CassandraRepository {
            @Query("INSERT INTO test(id, value) VALUES (:entity.id, :entity.value)")
            fun test(@Batch entity: List<TestEntity>)
        }
        """.trimIndent(), """
        data class TestEntity(val id: Long, val value: String?)    
        """.trimIndent())


        val columnDefinition = Mockito.mock(ColumnDefinition::class.java)
        whenever(columnDefinition.name).thenReturn(CqlIdentifier.fromCql("test"))
        val columnDefinitions = DefaultColumnDefinitions.valueOf(List.of(
            columnDefinition, columnDefinition
        ))
        val codecRegistry = Mockito.mock(CodecRegistry::class.java)
        whenever(executor.boundStatement.preparedStatement).thenReturn(executor.preparedStatement)
        whenever(executor.boundStatement.codecRegistry()).thenReturn(codecRegistry)
        whenever<TypeCodec<*>?>(codecRegistry.codecFor<Any>(Mockito.any(), Mockito.any(Class::class.java))).thenReturn(Mockito.mock(TypeCodec::class.java))
        whenever(executor.preparedStatement.variableDefinitions).thenReturn(columnDefinitions)
        var nextStmt: BoundStatementBuilder
        val c = Mockito.mockConstruction(BoundStatementBuilder::class.java) { mock: BoundStatementBuilder, context: MockedConstruction.Context? -> whenever(mock.build()).thenReturn(executor.boundStatement) }

        c.use {
            repository.invoke<Any>("test", listOf(new("TestEntity", 42, null), new("TestEntity", 43, "test")))
            nextStmt = c.constructed()[0]
        }

        val order = Mockito.inOrder(executor.boundStatementBuilder, nextStmt)

        order.verify(executor.boundStatementBuilder).setLong(0, 42)
        order.verify(executor.boundStatementBuilder).setToNull(1)
        order.verify(executor.boundStatementBuilder).build()

        order.verify(nextStmt).setLong(0, 43)
        order.verify(nextStmt).setString(1, "test")
        order.verify(nextStmt).build()

        order.verifyNoMoreInteractions()
    }

    @Test
    fun testUnknownTypeParameter() {
        val mapper = Mockito.mock(CassandraParameterColumnMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value)")
                fun test(id: Long, value: UnknownType)
            }
            
            """.trimIndent(), """
            public class UnknownType {}
            
            """.trimIndent())
        repository.invoke<Any>("test", 42L, new("UnknownType"))
        verify(executor.boundStatementBuilder).setLong(0, 42L)
        verify(mapper).apply(ArgumentMatchers.same(executor.boundStatementBuilder), ArgumentMatchers.eq(1), ArgumentMatchers.any())
    }

    @Test
    fun testUnknownTypeEntityField() {
        val mapper = Mockito.mock(CassandraParameterColumnMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0.f)")
                fun test(id: Long, value0: TestEntity)
            }
            
            """.trimIndent(), """
            class UnknownType {}
            """.trimIndent(), """
            data class TestEntity (val f: UnknownType)
            """.trimIndent())
        repository.invoke<Any>("test", 42L, new("TestEntity", new("UnknownType")))
        verify(executor.boundStatementBuilder).setLong(0, 42L)
        verify(mapper).apply(ArgumentMatchers.same(executor.boundStatementBuilder), ArgumentMatchers.eq(1), ArgumentMatchers.any())
    }

    @Test
    fun testNativeParameterNonFinalMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : CassandraParameterColumnMapper<String> {
                override fun apply(stmt: SettableByName<*>, index: Int, value0: String?) {
                    stmt.set(index, mapOf("test" to value0), Map::class.java)
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(id, value) VALUES (:id, :value0)")
                fun test(id: Long, @Mapping(TestMapper::class) value0: String);
            }
            
            """.trimIndent())
        repository.invoke<Any>("test", 42L, "test-value")
        Mockito.verify(executor.boundStatementBuilder).setLong(0, 42L)
        Mockito.verify(executor.boundStatementBuilder).set(1, mapOf("test" to "test-value"), Map::class.java)
    }

    @Test
    fun testMultipleParametersWithSameMapper() {
        val repository = compile(listOf(newGenerated("TestMapper")), """
            open class TestMapper : CassandraParameterColumnMapper<String> {
                override fun apply(stmt: SettableByName<*>, index: Int, value: String?) {
                    stmt.set(index, mapOf("test" to value), Map::class.java)
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : CassandraRepository {
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
            open class TestMapper : CassandraParameterColumnMapper<TestRecord> {
                override fun apply(stmt: SettableByName<*>, index: Int, value: TestRecord?) {
                    stmt.set(index, mapOf("test" to value.toString()), Map::class.java)
                }
            }
            
            """.trimIndent(), """
            @Repository
            interface TestRepository : CassandraRepository {
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
