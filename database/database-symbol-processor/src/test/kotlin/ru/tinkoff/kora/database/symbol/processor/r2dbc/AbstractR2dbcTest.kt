package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.database.symbol.processor.AbstractRepositoryTest

abstract class AbstractR2dbcTest : AbstractRepositoryTest() {
    protected val executor = MockR2dbcExecutor()

    override fun commonImports() = super.commonImports() + """
        import ru.tinkoff.kora.database.r2dbc.*;
        import ru.tinkoff.kora.database.r2dbc.mapper.result.*;
        import ru.tinkoff.kora.database.r2dbc.mapper.parameter.*;
        import java.util.concurrent.CompletionStage;
        import reactor.core.publisher.*;

        import io.r2dbc.spi.*;
        
        """.trimIndent()

    protected open fun compile(arguments: List<*>, @Language("kotlin") vararg sources: String) = this.compile(executor, arguments, *sources)

}
