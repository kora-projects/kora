package ru.tinkoff.kora.database.symbol.processor.vertx

import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import io.vertx.sqlclient.Tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatcher
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.vertx.repository.AllowedParametersRepository
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxParametersTest {
    private val executor = MockVertxExecutor()
    private val ctx = TestContext()
    private val repository: AllowedParametersRepository

    init {
        ctx.addContextElement(TypeRef.of(VertxConnectionFactory::class.java), executor)
        ctx.addMock(TypeRef.of(VertxParameterColumnMapper::class.java, TestEntity.UnknownField::class.java))
        ctx.addMock(TypeRef.of(VertxRowMapper::class.java, Int::class.javaObjectType))
        whenever(ctx.findInstance(TypeRef.of(VertxRowMapper::class.java, Int::class.javaObjectType)).apply(any())).thenReturn(1)
        ctx.addMock(TypeRef.of(TestEntityFieldVertxParameterColumnMapperNonFinal::class.java))
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository::class).java)
    }

    @BeforeEach
    internal fun setUp() {
//        executor.reset()
    }

    private fun eq(tuple: Tuple): ArgumentMatcher<Tuple> {
        return object : ArgumentMatcher<Tuple> {
            override fun toString(): String {
                return tuple.deepToString()
            }
            override fun matches(argument: Tuple): Boolean {
                if (argument.size() != tuple.size()) {
                    return false
                }
                for (i in 0 until tuple.size()) {
                    val o1 = tuple.getValue(i)
                    val o2 = argument.getValue(i)
                    if (o1 != o2) {
                        return false
                    }
                }
                return true
            }
        }
    }

    private fun listMatches(tuple: List<Tuple>): List<Tuple> {
        return argThat(listEq(tuple))
    }

    private fun listEq(tuples: List<Tuple>): ArgumentMatcher<List<Tuple>> {
        return object : ArgumentMatcher<List<Tuple>> {
            override fun matches(argument: List<Tuple>): Boolean {
                if (argument.size != tuples.size) {
                    return false
                }
                for (i in tuples.indices) {
                    val t1 = tuples[i]
                    val t2 = argument[i]
                    if (t1.size() != t2.size()) {
                        return false
                    }
                    for (j in 0 until t1.size()) {
                        val o1 = t1.getValue(i)
                        val o2 = t2.getValue(i)
                        if (o1 != o2) {
                            return false
                        }
                    }
                }
                return true
            }

            override fun toString(): String {
                return tuples.toString()
            }
        }
    }

    private fun matches(tuple: Tuple): Tuple {
        return argThat(eq(tuple))
    }

    @Test
    fun testNativeParameter() {
        repository.nativeParameter(null, 1)
        verify(executor.query).execute(matches(Tuple.of(null, 1)), any())
    }
//
//    @Test
//    fun testNativeParameter() {
//        repository.nativeParameter("test", 42).block()
//        verify(executor.query).execute(matches(Tuple.of("test", 42)))
//    }
//
//    @Test
//    fun testDtoParameter() {
//        repository.dtoParameter(Entity("val1", 42)).block()
//        Mockito.verify(executor.query).execute(matches(Tuple.of("val1", 42)))
//    }
//
//    @Test
//    fun testDtoFieldMapping() {
//        repository.dtoFieldMapping(EntityWithMappedField("val1", EntityWithMappedField.Field("val2"))).block()
//        Mockito.verify(executor.query).execute(matches(Tuple.of("val1", "val2")))
//    }
//
//    @Test
//    fun testDtoParameterMapping() {
//        repository.dtoParameterMapping(
//            MappedEntity(
//                MappedEntity.Field1("val1"),
//                MappedEntity.Field2("val2")
//            )
//        ).block()
//        Mockito.verify(executor.query).execute(matches(Tuple.of("val1", "val2")))
//    }
//
//    @Test
//    fun testNativeBatch() {
//        repository.nativeParameterBatch(listOf("test1", "test2"), 42).block()
//        Mockito.verify(executor.query).executeBatch(
//            listMatches(
//                listOf(
//                    Tuple.of("test1", 42),
//                    Tuple.of("test2", 42)
//                )
//            )
//        )
//    }
//
//    @Test
//    fun testDtoBatch() {
//        repository.dtoBatch(
//            listOf(
//                Entity("test1", 42),
//                Entity("test2", 43)
//            )
//        ).block()
//        Mockito.verify(executor.query).executeBatch(
//            listMatches(
//                listOf(
//                    Tuple.of("test1", 42),
//                    Tuple.of("test2", 43)
//                )
//            )
//        )
//    }
//
//    @Test
//    fun testMappedDtoBatch() {
//        repository.mappedBatch(
//            listOf(
//                MappedEntity(MappedEntity.Field1("val1"), MappedEntity.Field2("val2")),
//                MappedEntity(MappedEntity.Field1("val3"), MappedEntity.Field2("val4"))
//            )
//        ).block()
//        Mockito.verify(executor.query).executeBatch(
//            listMatches(
//                listOf(
//                    Tuple.of("val1", "val2"),
//                    Tuple.of("val3", "val4")
//                )
//            )
//        )
//    }
//
//    @Test
//    fun testParametersWithSimilarNames() {
//        runBlocking {
//            repository.parametersWithSimilarNames("test", 42)
//        }
//        verify(executor.connection).preparedQuery("INSERT INTO test(value1, value2) VALUES ($1, $2)")
//        verify(executor.query).execute(matches(Tuple.of("test", 42)))
//    }
}
