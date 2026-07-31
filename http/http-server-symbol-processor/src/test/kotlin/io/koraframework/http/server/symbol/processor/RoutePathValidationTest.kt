package io.koraframework.http.server.symbol.processor

import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class RoutePathValidationTest : AbstractHttpControllerTest() {

    @Test
    fun shouldAcceptWildcardInFinalPathSegment() {
        compile(
            """
            @HttpController("/api")
            class Controller {
                @HttpRoute(method = "GET", path = "/files/*")
                fun catchAll(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/files/*.js")
                fun extension(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/files/file-*.txt")
                fun infix(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/foo*bar")
                fun embedded(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/tenant/{id}/report-*.json")
                fun parameterized(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        )
    }

    @Test
    fun shouldRejectInvalidWildcardPlacement() {
        val invalidPaths = listOf(
            "/foo/*/bar",
            "/foo/**",
            "/foo/a*b*c",
            "/foo/*/",
            "/foo/{id}/asset-*/detail",
            "/foo/{*}/bar",
            "/foo/{*}",
            "/foo/{id*}"
        )

        for (path in invalidPaths) {
            val result = compile0(
                listOf(HttpControllerProcessorProvider()),
                source("", path)
            ).assertFailure()

            Assertions.assertThat(result.messages)
                .describedAs(path)
                .anyMatch { message -> message.contains(error(path)) }
        }
    }

    @Test
    fun shouldValidateCombinedControllerAndRoutePath() {
        val result = compile0(
            listOf(HttpControllerProcessorProvider()),
            source("/api/*", "/details")
        ).assertFailure()

        Assertions.assertThat(result.messages)
            .anyMatch { message -> message.contains(error("/api/*/details")) }
    }

    private fun source(rootPath: String, routePath: String): String {
        return """
            @HttpController("$rootPath")
            class Controller {
                @HttpRoute(method = "GET", path = "$routePath")
                fun test(): HttpServerResponse = HttpServerResponse.of(200)
            }
        """.trimIndent()
    }

    private fun error(path: String): String {
        return "Wildcard '*' is only allowed once and in the final path segment: $path" +
            ". Valid examples: /files/* and /files/*.js"
    }
}
