package ru.tinkoff.kora.http.server.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest


class ExtensionTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.server.common.handler.*
            import ru.tinkoff.kora.http.server.common.*
            import ru.tinkoff.kora.http.common.*
            """.trimIndent()
    }

    @Test
    fun testExtensionWithTag() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface Controller {
                @Root
                fun root(@Tag(String::class) mapper: HttpServerResponseMapper<HttpResponseEntity<String>>): String = ""
            
                @Tag(String::class)
                fun mapper() : HttpServerResponseMapper<String> = HttpServerResponseMapper<String> { rq, result  -> HttpServerResponse.of(200) }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionWithoutTag() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface Controller {
                @Root
                fun root(mapper: HttpServerResponseMapper<HttpResponseEntity<String>>): String = ""
            
                fun mapper() : HttpServerResponseMapper<String> = HttpServerResponseMapper<String> { rq, result  -> HttpServerResponse.of(200) }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionWithNullable() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface Controller {
                @Root
                fun root(mapper: HttpServerResponseMapper<HttpResponseEntity<String?>>): String = ""
            
                fun mapper() : HttpServerResponseMapper<String> = HttpServerResponseMapper<String> { rq, result  -> HttpServerResponse.of(200) }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
    }
}
