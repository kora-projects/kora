package io.koraframework.http.server.symbol.processor

import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SuspendHttpControllerTest : AbstractHttpControllerTest() {
    @Test
    fun testSuspendMethodIsRejected() {
        val result = compile0(
            listOf(HttpControllerProcessorProvider()),
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        ).assertFailure()

        assertThat(result.messages).anySatisfy {
            assertThat(it)
                .contains("Suspend methods are not supported by the HTTP server controller generator")
                .contains("--enable-preview")
                .contains("StructuredTaskScope.open")
                .contains("Remove suspend from the controller method")
        }
    }
}
