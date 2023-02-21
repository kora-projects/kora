package ru.tinkoff.kora.database.symbol.processor.vertx

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.database.common.UpdateCount
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper

class VertxResultsTest : AbstractVertxRepositoryTest() {

    @Test
    fun testReturnSuspendObject() {
        val mapper = mock(VertxRowSetMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int
            }
            """.trimIndent())
        whenever(mapper.apply(any())).thenReturn(42)
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
        verify(mapper).apply(executor.rowSet)
    }

    @Test
    fun testReturnSuspendVoid() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testReturnSuspendUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                suspend fun test(): UpdateCount
            }
            
            """.trimIndent())
        whenever(executor.rowSet.rowCount()).thenReturn(42)
        val result = repository.invoke<UpdateCount>("test")
        assertThat(result?.value).isEqualTo(42)
        verify(executor.connection).preparedQuery("INSERT INTO test(value) VALUES ('test')")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testReturnBatchUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                suspend fun test(@Batch value: List<String>): UpdateCount
            }
            
            """.trimIndent())
        whenever(executor.rowSet.rowCount()).thenReturn(42)
        val result = repository.invoke<UpdateCount>("test", listOf("test1", "test2"))
        assertThat(result?.value).isEqualTo(42)
        verify(executor.connection).preparedQuery("INSERT INTO test(value) VALUES ($1)")
        verify(executor.query).executeBatch(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testFinalResultSetMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                suspend fun test(): Int
            }
            
            """.trimIndent(), """
            class TestResultMapper : VertxRowSetMapper<Int> {
                override fun apply(rs: RowSet<Row>): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testNonFinalFinalResultSetMapper() {
        val repository = compile(listOf(newGenerated("TestResultMapper")), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                suspend fun test(): Int
            }
            
            """.trimIndent(), """
            open class TestResultMapper : VertxRowSetMapper<Int> {
                override fun apply(rs: RowSet<Row>): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testOneWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): Int?
            }
            
            """.trimIndent(), """
            class TestRowMapper : VertxRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRow(MockVertxExecutor.MockColumn("count", 0))
        var result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
        executor.reset()
        executor.setRows(listOf<List<MockVertxExecutor.MockColumn>>())
        result = repository.invoke<Int>("test")
        assertThat(result).isNull()
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testOneWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): Int?
            }
            
            """.trimIndent(), """
            open class TestRowMapper : VertxRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRow(MockVertxExecutor.MockColumn("count", 0))
        var result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
        executor.reset()
        executor.setRows(listOf())
        result = repository.invoke<Int>("test")
        assertThat(result).isNull()
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testListWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): List<Int>
            }
            
            """.trimIndent(), """
            class TestRowMapper : VertxRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRows(listOf(
            listOf(MockVertxExecutor.MockColumn("count", 0)),
            listOf(MockVertxExecutor.MockColumn("count", 0))
        ))
        val result = repository.invoke<List<Int>>("test")
        assertThat(result).contains(42, 42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun testListWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : VertxRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): List<Int>
            }
            
            """.trimIndent(), """
            open class TestRowMapper : VertxRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRows(listOf(
            listOf(MockVertxExecutor.MockColumn("count", 0)),
            listOf(MockVertxExecutor.MockColumn("count", 0))
        ))
        val result = repository.invoke<List<Int>>("test")
        assertThat(result).contains(42, 42)
        verify(executor.connection).preparedQuery("SELECT count(*) FROM test")
        verify(executor.query).execute(ArgumentMatchers.any(), ArgumentMatchers.any())
        executor.reset()
    }

}
