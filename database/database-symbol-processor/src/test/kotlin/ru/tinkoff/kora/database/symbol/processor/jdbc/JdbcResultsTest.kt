package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.common.UpdateCount
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import java.util.concurrent.Executor
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.jvmErasure

class JdbcResultsTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testReturnVoid() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES ('value')")
        verify(executor.preparedStatement).execute()
    }

    @Test
    fun testReturnObject() {
        val mapper = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                fun test(): Int
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        val result = repository.invoke<Any>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        verify(mapper).apply(executor.resultSet)
        executor.reset()
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(null)
        Assertions.assertThatThrownBy { repository.invoke<Any>("test") }.isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun testReturnNullableObject() {
        val mapper = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Nullable
                @Query("SELECT count(*) FROM test")
                fun test(): Int?
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        var result = repository.invoke<Any>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        verify(mapper).apply(executor.resultSet)
        executor.reset()

        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(null)
        result = repository.invoke<Any>("test")
        Assertions.assertThat(result).isNull()
    }

    @Test
    fun testReturnSuspendObject() {
        val e = Executor { command -> Thread(command).start() }
        val ctxKey = object : Context.Key<String>() {
            override fun copy(`object`: String?) = TODO("Not yet implemented")
        }
        Context.current()[ctxKey] = "test"
        val mapper = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(listOf(e, mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int
            }
            
            """.trimIndent())
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        whenever(executor.mockConnection.prepareStatement(any())).then {
            require(Context.current()[ctxKey] == "test")
            executor.preparedStatement
        }
        val result = repository.invoke<Any>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        verify(mapper).apply(executor.resultSet)
    }

    @Test
    fun testReturnSuspendVoid() {
        val e = Executor { command -> command.run() }
        val repository = compile(listOf<Any>(e), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).execute()
    }

    @Test
    fun testReturnUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                fun test(): UpdateCount
            }
            
            """.trimIndent())
        whenever(executor.preparedStatement.executeLargeUpdate()).thenReturn(42L)
        val result = repository.invoke<UpdateCount>("test")
        Assertions.assertThat(result?.value).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES ('test')")
        verify(executor.preparedStatement).executeLargeUpdate()
    }

    @Test
    fun testReturnBatchUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                fun test(@Batch value: List<String>): UpdateCount
            }
            
            """.trimIndent())
        whenever(executor.preparedStatement.executeLargeBatch()).thenReturn(longArrayOf(42, 43))
        val result = repository.invoke<UpdateCount>("test", listOf("test1", "test2"))
        Assertions.assertThat(result?.value).isEqualTo(85)
        verify(executor.mockConnection).prepareStatement("INSERT INTO test(value) VALUES (?)")
        verify(executor.preparedStatement).executeLargeBatch()
    }

    @Test
    fun testFinalResultSetMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                fun test(): Int
            }
            
            """.trimIndent(), """
            class TestResultMapper : JdbcResultSetMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
    }

    @Test
    fun testNonFinalFinalResultSetMapper() {
        val repository = compile(listOf(newGenerated("TestResultMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                fun test(): Int
            }
            
            """.trimIndent(), """
            open class TestResultMapper : JdbcResultSetMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
    }

    @Test
    fun testOneWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test(): Int?
            }
            
            """.trimIndent(), """
            class TestRowMapper : JdbcRowMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        whenever(executor.resultSet.next()).thenReturn(true, false)
        var result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        executor.reset()
        whenever(executor.resultSet.next()).thenReturn(false, false)
        result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isNull()
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
    }

    @Test
    fun testOneWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test(): Int?
            }
            
            """.trimIndent(), """
            open class TestRowMapper : JdbcRowMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        whenever(executor.resultSet.next()).thenReturn(true, false)
        var result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        executor.reset()
        whenever(executor.resultSet.next()).thenReturn(false, false)
        result = repository.invoke<Int>("test")
        Assertions.assertThat(result).isNull()
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
    }

    @Test
    fun testListWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test(): List<Int>
            }
            
            """.trimIndent(), """
            class TestRowMapper : JdbcRowMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        whenever(executor.resultSet.next()).thenReturn(true, true, false)
        val result = repository.invoke<List<Int>>("test")
        Assertions.assertThat(result).contains(42, 42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
    }

    @Test
    fun testListWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                fun test(): List<Int>
            }
            
            """.trimIndent(), """
            open class TestRowMapper : JdbcRowMapper<Int> {
                override fun apply(rs: ResultSet): Int? {
                  return 42
                }
            }
            
            """.trimIndent())
        whenever(executor.resultSet.next()).thenReturn(true, true, false)
        val result = repository.invoke<List<Int>>("test")
        Assertions.assertThat(result).contains(42, 42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        executor.reset()
    }

    @Test
    fun testMultipleMethodsWithSameReturnType() {
        val mapper = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
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
            interface TestRepository : JdbcRepository {
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
            open class TestRowMapper : JdbcRowMapper<Int> {
                override fun apply(row: ResultSet): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testMethodsWithSameName() {
        val mapper1 = Mockito.mock(JdbcResultSetMapper::class.java)
        val mapper2 = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(listOf(mapper1, mapper2), """
            @Repository
            interface TestRepository : JdbcRepository {
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
        val mapper = Mockito.mock(JdbcResultSetMapper::class.java)
        val repository = compile(
            listOf(mapper), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Tag(TestRepository::class)
                @Query("SELECT count(*) FROM test")
                fun test(): Int
            }
            
            """.trimIndent()
        )
        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn(42)
        val result = repository.invoke<Any>("test")
        Assertions.assertThat(result).isEqualTo(42)
        verify(executor.mockConnection).prepareStatement("SELECT count(*) FROM test")
        verify(executor.preparedStatement).executeQuery()
        verify(mapper).apply(executor.resultSet)

        val mapperConstructorParameter = repository.objectClass.constructors.first().parameters[1]
        Assertions.assertThat(mapperConstructorParameter.type.jvmErasure).isEqualTo(JdbcResultSetMapper::class)
        val tag = mapperConstructorParameter.findAnnotations(Tag::class).first()
        Assertions.assertThat(tag).isNotNull()
        Assertions.assertThat(tag.value.map { it.java }).isEqualTo(listOf(compileResult.loadClass("TestRepository")))
    }

}
