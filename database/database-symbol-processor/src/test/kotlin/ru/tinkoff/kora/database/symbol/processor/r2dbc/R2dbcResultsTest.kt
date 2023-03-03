package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.common.UpdateCount
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper

class R2dbcResultsTest : AbstractR2dbcTest() {

    @Test
    fun testReturnSuspendObject() {
        val mapper = Mockito.mock(R2dbcResultFluxMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int
            }
            
            """.trimIndent())
        whenever(mapper.apply(any())).thenReturn(Mono.just(42))
        val result = repository.invoke<Any>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
        verify(mapper).apply(any())
    }

    @Test
    fun testReturnSuspendNullableObject() {
        val mapper = Mockito.mock(R2dbcResultFluxMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test(): Int?
            }
            
            """.trimIndent())
        whenever(mapper.apply(any())).thenReturn(Mono.just(42))
        var result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
        verify(mapper).apply(any())

        whenever(mapper.apply(any())).thenReturn(Mono.empty<Any>())
        executor.reset()
        executor.setRows(listOf())
        result = repository.invoke("test")
        assertThat(result).isNull()
    }

    @Test
    fun testReturnSuspendUnit() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                suspend fun test()
            }
            
            """.trimIndent())
        repository.invoke<Any>("test")
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testReturnUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(value) VALUES ('test')")
                suspend fun test(): UpdateCount
            }
            
            """.trimIndent())
        executor.setUpdateCountResult(42)
        val result = repository.invoke<UpdateCount>("test")
        assertThat(result?.value).isEqualTo(42)
        verify(executor.con).createStatement("INSERT INTO test(value) VALUES ('test')")
        verify(executor.statement).execute()
    }

    @Test
    fun testReturnBatchUpdateCount() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("INSERT INTO test(value) VALUES (:value)")
                suspend fun test(@ru.tinkoff.kora.database.common.annotation.Batch value: List<String>): UpdateCount
            }
            
            """.trimIndent())
        executor.setUpdateCountResult(42)
        val result = repository.invoke<UpdateCount>("test", listOf("test1", "test2"))
        assertThat(result?.value).isEqualTo(42)
        verify(executor.con).createStatement("INSERT INTO test(value) VALUES ($1)")
        verify(executor.statement).execute()
    }

    @Test
    fun testFinalResultSetMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                suspend fun test(): Int
            }
            
            """.trimIndent(), """
            class TestResultMapper : R2dbcResultFluxMapper<Int, Mono<Int>> {
                override fun apply(rs: Flux<Result>): Mono<Int> {
                  return Mono.just(42);
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testNonFinalFinalResultSetMapper() {
        val repository = compile(listOf(newGenerated("TestResultMapper")), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestResultMapper::class)
                suspend fun test(): Int
            }
            
            """.trimIndent(), """
            open class TestResultMapper : R2dbcResultFluxMapper<Int, Mono<Int>> {
                override fun apply(rs: Flux<Result>): Mono<Int> {
                  return Mono.just(42);
                }
            }
            
            """.trimIndent())
        val result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testOneWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): Int?
            }
            
            """.trimIndent(), """
            class TestRowMapper : R2dbcRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRow(MockR2dbcExecutor.MockColumn("count", 0))
        var result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
        executor.reset()
        executor.setRows(listOf())
        result = repository.invoke<Int>("test")
        assertThat(result).isNull()
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testOneWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): Int?
            }
            
            """.trimIndent(), """
            open class TestRowMapper : R2dbcRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRow(MockR2dbcExecutor.MockColumn("count", 0))
        var result = repository.invoke<Int>("test")
        assertThat(result).isEqualTo(42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
        executor.reset()
        executor.setRows(listOf())
        result = repository.invoke<Int>("test")
        assertThat(result).isNull()
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testListWithFinalRowMapper() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): List<Int>
            }
            
            """.trimIndent(), """
            class TestRowMapper : R2dbcRowMapper<Int> {
                override fun apply(row: Row) : Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRows(listOf(
            listOf(MockR2dbcExecutor.MockColumn("count", 0)),
            listOf(MockR2dbcExecutor.MockColumn("count", 0))
        ))
        val result = repository.invoke<List<Int>>("test")
        assertThat(result).contains(42, 42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
    }

    @Test
    fun testListWithNonFinalRowMapper() {
        val repository = compile(listOf(newGenerated("TestRowMapper")), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test")
                @Mapping(TestRowMapper::class)
                suspend fun test(): List<Int>
            }
            
            """.trimIndent(), """
            open class TestRowMapper : R2dbcRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
        executor.setRows(listOf(
            listOf(MockR2dbcExecutor.MockColumn("count", 0)),
            listOf(MockR2dbcExecutor.MockColumn("count", 0))
        ))
        val result = repository.invoke<List<Int>>("test")
        assertThat(result).contains(42, 42)
        verify(executor.con).createStatement("SELECT count(*) FROM test")
        verify(executor.statement).execute()
        executor.reset()
    }


    @Test
    fun testMultipleMethodsWithSameReturnType() {
        val mapper = Mockito.mock(R2dbcResultFluxMapper::class.java)
        val repository = compile(listOf(mapper), """
            @Repository
            interface TestRepository : R2dbcRepository {
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
            interface TestRepository : R2dbcRepository {
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
            open class TestRowMapper : R2dbcRowMapper<Int> {
                override fun apply(row: Row): Int {
                  return 42;
                }
            }
            
            """.trimIndent())
    }

    @Test
    fun testMethodsWithSameName() {
        val mapper1 = Mockito.mock(R2dbcResultFluxMapper::class.java)
        val mapper2 = Mockito.mock(R2dbcResultFluxMapper::class.java)
        val repository = compile(listOf(mapper1, mapper2), """
            @Repository
            interface TestRepository : R2dbcRepository {
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: Int): Int
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: Long): Int
                @Query("SELECT count(*) FROM test WHERE test = :test")
                fun test1(test: String): Long
            }
            
            """.trimIndent())
    }
}
