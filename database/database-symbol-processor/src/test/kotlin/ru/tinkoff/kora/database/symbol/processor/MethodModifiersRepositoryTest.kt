package ru.tinkoff.kora.database.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest

class MethodModifiersRepositoryTest : AbstractJdbcRepositoryTest() {
    @Test
    fun testInterfacePublicFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            interface TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                fun test()
            }
            
            """.trimIndent())
    }

    @Test
    fun testAbstractClassPublicFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            abstract class TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                abstract fun test()
            }
            
            """.trimIndent())
    }

    @Test
    fun testAbstractClassProtectedFun() {
        val repository = compile(listOf<Any>(), """
            @Repository
            abstract class TestRepository : JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                protected abstract fun test()
            }
            
            """.trimIndent())
    }

}
