package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.sqlclient.Tuple
import org.intellij.lang.annotations.Language
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.argThat
import ru.tinkoff.kora.database.symbol.processor.AbstractRepositoryTest

abstract class AbstractVertxRepositoryTest : AbstractRepositoryTest() {
    val executor = MockVertxExecutor()

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.vertx.*;
            import ru.tinkoff.kora.database.vertx.mapper.result.*;
            import ru.tinkoff.kora.database.vertx.mapper.parameter.*;
            import java.util.concurrent.CompletionStage;
            import reactor.core.publisher.*;
            import io.vertx.sqlclient.SqlConnection;
            import io.vertx.sqlclient.SqlClient;
            
            """.trimIndent()
    }

    protected open fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = this.compile(executor, arguments, *sources)

    protected fun List<Tuple>.matcher() = argThat(tupleListMatcher(this))
    protected fun Tuple.matcher() = argThat(tupleMatcher(this))

    private fun tupleMatcher(tuple: Tuple): ArgumentMatcher<Tuple> {
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

    private fun tupleListMatcher(tuples: List<Tuple>): ArgumentMatcher<List<Tuple>> {
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

}
