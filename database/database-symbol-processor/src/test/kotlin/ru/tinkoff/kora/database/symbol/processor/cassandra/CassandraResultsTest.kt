package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.cql.Statement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraAsyncResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import java.util.concurrent.CompletableFuture
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class CassandraResultsTest : AbstractCassandraRepositoryTest() {
    @Test
    fun testReturnVoid() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.mockSession).prepare("INSERT INTO test(value) VALUES ('value')")
        verify(executor.mockSession).execute(ArgumentMatchers.any(Statement::class.java))
    }

    @Test
    fun testReturnObject() {
        val mapper = Mockito.mock(CassandraResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                fun test(): Int
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.mockSession).prepare("SELECT count(*) FROM test")
        verify(executor.mockSession).execute(ArgumentMatchers.any(Statement::class.java))
        verify(mapper).apply(executor.resultSet)
        executor.reset()

        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(null)
        assertThatThrownBy { repository.invoke<Any>("test") }.isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun testReturnNullableObject() {
        val mapper = Mockito.mock(CassandraResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                fun test(): Int?
            }
            
            """.trimIndent())
        val mapperType = repository.objectClass.primaryConstructor!!.parameters[1].type
        assertThat(mapperType.jvmErasure.java).isEqualTo(CassandraResultSetMapper::class.java)
        assertThat(mapperType.arguments.first().type?.classifier).isEqualTo(Int::class)
        assertThat(mapperType.arguments.first().type?.isMarkedNullable).isFalse()

        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        var result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.mockSession).prepare("SELECT count(*) FROM test")
        verify(executor.mockSession).execute(ArgumentMatchers.any(Statement::class.java))
        verify(mapper).apply(executor.resultSet)

        executor.reset()
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(null)
        result = repository.invoke<Any>("test")
        assertThat(result).isNull()
        verify(executor.mockSession).prepare("SELECT count(*) FROM test")
        verify(executor.mockSession).execute(ArgumentMatchers.any(Statement::class.java))
    }

    @Test
    fun testReturnSuspendObject() {
        val mapper = Mockito.mock(CassandraAsyncResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(42))
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test")
        verify(executor.mockSession).executeAsync(ArgumentMatchers.any(Statement::class.java))
    }

    @Test
    fun testReturnSuspendNullableObject() {
        val mapper = Mockito.mock(CassandraAsyncResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int?
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(42))
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test")
        verify(executor.mockSession).executeAsync(ArgumentMatchers.any(Statement::class.java))
    }

    @Test
    fun testReturnFlow() {
        val mapper = Mockito.mock(CassandraRowMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                fun test(): kotlinx.coroutines.flow.Flow<Int>
            }
            
            """.trimIndent())
        whenever(executor.mockSession.executeAsync(any<Statement<*>>())).thenReturn(CompletableFuture.completedFuture(MockCassandraExecutor.MockAsyncResultSet(listOf(mock(Row::class.java)))))
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        val result = repository.invoke<Flow<Int>>("test")!!
        runBlocking {
            assertThat(result.toList()).containsExactly(42)
            verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test")
            verify(executor.mockSession).executeAsync(ArgumentMatchers.any(Statement::class.java))
        }
    }

    @Test
    fun testReturnSuspendVoid() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.mockSession).prepareAsync("SELECT count(*) FROM test")
        verify(executor.mockSession).executeAsync(ArgumentMatchers.any(Statement::class.java))
    }

    @Test
    fun testMultipleMethodsWithSameReturnType() {
        val mapper = Mockito.mock(CassandraResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                fun test1(): Int
                @Query("SELECT count(*) FROM test")
                fun test2(): Int
                @Query("SELECT count(*) FROM test")
                fun test3(): Int
            }
            
            """.trimIndent())
    }

    @Test
    fun testMultipleMethodsWithSameMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test1(): Int
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test2(): Int
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test3(): Int
            }
            
            """.trimIndent(), """
            open class TestRowMapper : CassandraRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testMethodsWithSameName() {
        val mapper1 = Mockito.mock(CassandraResultSetMapper::class.java)
        val mapper2 = Mockito.mock(CassandraResultSetMapper::class.java)
        val repository = compile(listOf(mapper1, mapper2), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: Int): Int
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: Long): Int
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: String): Long
            }
            
            """.trimIndent()
        )
    }

    @Test
    fun testTaggedResult() {
        val mapper = Mockito.mock(CassandraResultSetMapper::class.java)
        val repository = compile(
            listOf(mapper), """
            @Repository
            interface TestRepository : CassandraRepository {
                @Query("SELECT count(*) FROM test")
                @Tag(TestRepository::class)
                fun test(): Int
            }
            
            """.trimIndent()
        )
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.mockSession).prepare("SELECT count(*) FROM test")
        verify(executor.mockSession).execute(ArgumentMatchers.any(Statement::class.java))
        verify(mapper).apply(executor.resultSet)

        val mapperConstructorParameter = repository.objectClass.constructors.first().parameters[1]
        assertThat(mapperConstructorParameter.type.jvmErasure).isEqualTo(CassandraResultSetMapper::class)
        val tag = mapperConstructorParameter.findAnnotations(Tag::class).first()
        assertThat(tag).isNotNull()
        assertThat(tag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("TestRepository")))
    }
}
