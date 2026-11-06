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
                .anySatisfy { message -> assertWildcardError(message, path) }
        }
    }

    @Test
    fun shouldValidateCombinedControllerAndRoutePath() {
        val result = compile0(
            listOf(HttpControllerProcessorProvider()),
            source("/api/*", "/details")
        ).assertFailure()

        Assertions.assertThat(result.messages)
            .anySatisfy { message -> assertWildcardError(message, "/api/*/details") }
    }

    @Test
    fun shouldRejectPathParameterAbsentFromRoute() {
        val result = compile0(
            listOf(HttpControllerProcessorProvider()), """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/users/{userId}")
                fun test(@Path id: String): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        ).assertFailure()

        Assertions.assertThat(result.messages)
            .anySatisfy { message -> Assertions.assertThat(message).contains("Path parameter 'id' is not present in the request mapping path") }
    }

    @Test
    fun shouldNormalizeControllerAndRoutePathAndMethod() {
        val module = compile(
            """
            @HttpController("/api/")
            class Controller {
                @HttpRoute(method = "get", path = "users")
                fun users(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/items")
                fun items(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        )

        val users = module.getHandler("get_api_users")
        Assertions.assertThat(users.routeTemplate()).isEqualTo("/api/users")
        Assertions.assertThat(users.method()).isEqualTo("GET")
        Assertions.assertThat(module.getHandler("get_api_items").routeTemplate()).isEqualTo("/api/items")
    }

    @Test
    fun shouldMapRootControllerWithEmptyRoutePathToSlash() {
        val module = compile(
            """
            @HttpController("/")
            class Controller {
                @HttpRoute(method = "GET", path = "")
                fun index(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        )

        val handlerMethod = loadClass("ControllerModule").methods.single()
        Assertions.assertThat(module.getHandler(handlerMethod.name).routeTemplate()).isEqualTo("/")
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

    private fun assertWildcardError(message: String, path: String) {
        Assertions.assertThat(message)
            .contains("HTTP server route path is invalid")
            .contains(path)
            .contains("Wildcard '*' is only allowed once")
            .contains("Fix:")
    }
}
