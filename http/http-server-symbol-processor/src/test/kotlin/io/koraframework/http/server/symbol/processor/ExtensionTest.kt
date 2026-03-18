package io.koraframework.http.server.symbol.processor

import org.junit.jupiter.api.Test
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest


class ExtensionTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.http.server.common.request.*
            import io.koraframework.http.server.common.response.*
            import io.koraframework.http.server.common.*
            import io.koraframework.http.common.*
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
