package io.koraframework.http.server.symbol.processor

import io.koraframework.http.server.common.request.HttpServerRequestHandler
import io.koraframework.http.server.common.response.mapper.HttpServerResponseEntityMapper
import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class BlockingHttpControllerTest : AbstractHttpControllerTest() {
    @Test
    fun testReturnBlockingResponse() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnBlockingResponseWithQueryParameter() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(@Query queryParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "GET", "/test?queryParameter")
            .hasStatus(400)
            .hasBody("Query parameter 'queryParameter' is required")
    }

    @Test
    fun testReturnBlockingResponseWithRequestParameter() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(bodyParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter))
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test", stringRequestMapper())
        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnBlockingVoid() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test() {
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnBlockingObject() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): String {
                    return "test"
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test", strResponseMapper())
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnBlockingResponseEntityObject() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpResponseEntity<String> {
                    return HttpResponseEntity.of(403, HttpHeaders.of("test-header", "test-value"), "test")
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test",
            HttpServerResponseEntityMapper(
                strResponseMapper()
            )
        )
        assertThat(handler, "GET", "/test")
            .hasStatus(403)
            .hasBody("test")
            .hasHeader("test-header", "test-value")
    }

    @Test
    fun testWithInterceptor() {
        val module = this.compile(
            """
            @HttpController
            @InterceptWith(TestInterceptor1::class)
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2::class)
                fun test(): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent(), """
            class TestInterceptor1 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400)
                    return chain.process(request)
                }
            }
            
            """.trimIndent(), """
            class TestInterceptor2 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400)
                    return chain.process(request)
                }
            }
            
            """.trimIndent()
        )
        val handler: HttpServerRequestHandler = module.getHandler("get_test", new("TestInterceptor1"), new("TestInterceptor2"))
        assertThat(handler, "GET", "/test?test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testWithInterceptorWithParameters() {
        val module = this.compile(
            """
            @HttpController
            @InterceptWith(TestInterceptor1::class)
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2::class)
                fun test(@Query queryParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            """.trimIndent(), """
            class TestInterceptor1 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400)
                    return chain.process(request)
                }
            }
            """.trimIndent(), """
            class TestInterceptor2 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400)
                    return chain.process(request)
                }
            }
            """.trimIndent()
        )

        val handler: HttpServerRequestHandler = module.getHandler("get_test", new("TestInterceptor1"), new("TestInterceptor2"))
        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testParametersReadRequestModifiedByInterceptor() {
        val module = this.compile(
            """
            @HttpController
            @InterceptWith(TestInterceptor::class)
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(@Header("X-User") user: String, @Query limit: Int, request: HttpServerRequest): HttpServerResponse {
                    return HttpServerResponse.of(200, HttpBody.plaintext(user + ":" + limit + ":" + request.headers().getFirst("X-User")))
                }
            }
            """.trimIndent(), """
            class TestInterceptor : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    return chain.process(request.toBuilder().header("X-User", "alice").queryParam("limit", 10).build())
                }
            }
            """.trimIndent()
        )

        val handler: HttpServerRequestHandler = module.getHandler("get_test", new("TestInterceptor"))
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("alice:10:alice")
    }

    @Test
    fun testReturnNullableResponse() {
        compile0(
            listOf(HttpControllerProcessorProvider(), KoraAppProcessorProvider()), """
            @Component
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponse? = HttpServerResponse.of(200)
            }
            """.trimIndent(), """
            @KoraApp
            interface App : ControllerModule {
                @Root
                fun root(handlers: io.koraframework.application.graph.All<io.koraframework.http.server.common.request.HttpServerRequestHandler>): String = ""
            }
            """.trimIndent()
        ).assertSuccess()
    }

    @Test
    fun testDistinctRoutesGetDistinctHandlers() {
        val module = this.compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/files")
                fun files(): HttpServerResponse = HttpServerResponse.of(200, HttpBody.plaintext("files"))

                @HttpRoute(method = "GET", path = "/files/*")
                fun file(): HttpServerResponse = HttpServerResponse.of(200, HttpBody.plaintext("file"))

                @HttpRoute(method = "GET", path = "/users/{id}")
                fun userById(@Path id: String): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/users/id")
                fun userId(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/привет")
                fun hello(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "GET", path = "/мир")
                fun world(): HttpServerResponse = HttpServerResponse.of(200)

                @HttpRoute(method = "VERSION-CONTROL", path = "/doc")
                fun versionControl(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent()
        )

        Assertions.assertThat(loadClass("ControllerModule").methods).hasSize(7)
        assertThat(module.getHandler("get_files"), "GET", "/files").hasBody("files")
        assertThat(module.getHandler("get_files_file"), "GET", "/files/a").hasBody("file")
        Assertions.assertThat(module.getHandler("version_control_doc").method()).isEqualTo("VERSION-CONTROL")
    }

    @Test
    fun testRouteInheritedFromInterface() {
        val module = this.compile(
            """
            interface Api {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor1::class)
                fun test(@Query value: String): HttpServerResponse
            }
            """.trimIndent(), """
            @HttpController
            class Controller : Api {
                @InterceptWith(TestInterceptor2::class)
                override fun test(value: String): HttpServerResponse = HttpServerResponse.of(200, HttpBody.plaintext(value))
            }
            """.trimIndent(), """
            class TestInterceptor1 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    return chain.process(request)
                }
            }
            """.trimIndent(), """
            class TestInterceptor2 : HttpServerInterceptor {
                override fun intercept(request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : HttpServerResponse {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(403)
                    return chain.process(request)
                }
            }
            """.trimIndent()
        )

        val handler: HttpServerRequestHandler = module.getHandler("get_test", new("TestInterceptor1"), new("TestInterceptor2"))
        assertThat(handler, "GET", "/test?value=test")
            .hasStatus(200)
            .hasBody("test")
        assertThat(handler, "GET", "/test")
            .hasStatus(403)
    }

    @Test
    fun testRouteInheritedFromGenericInterface() {
        val module = this.compile(
            """
            interface Api<T> {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent(), """
            @HttpController
            class Controller : Api<String>
            """.trimIndent()
        )

        assertThat(module.getHandler("get_test"), "GET", "/test")
            .hasStatus(200)
    }

    @Test
    fun testControllersSharingBaseClassRoute() {
        compile0(
            listOf(HttpControllerProcessorProvider(), KoraAppProcessorProvider()), """
            abstract class Base {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponse = HttpServerResponse.of(200)
            }
            """.trimIndent(), """
            @Component
            @HttpController("/a")
            class A : Base()
            """.trimIndent(), """
            @Component
            @HttpController("/b")
            class B : Base()
            """.trimIndent(), """
            @KoraApp
            interface App : AModule, BModule {
                @Root
                fun root(handlers: io.koraframework.application.graph.All<io.koraframework.http.server.common.request.HttpServerRequestHandler>): String = ""
            }
            """.trimIndent()
        ).assertSuccess()
    }

    @Test
    fun testNestedControllersWithSameSimpleName() {
        compile0(
            listOf(HttpControllerProcessorProvider()), """
            class UserApi {
                @HttpController
                class Controller {
                    @HttpRoute(method = "GET", path = "/users")
                    fun get(): HttpServerResponse = HttpServerResponse.of(200)
                }
            }
            """.trimIndent(), """
            class OrderApi {
                @HttpController
                class Controller {
                    @HttpRoute(method = "GET", path = "/orders")
                    fun get(): HttpServerResponse = HttpServerResponse.of(200)
                }
            }
            """.trimIndent()
        ).assertSuccess()

        Assertions.assertThat(loadClass("UserApi_ControllerModule").methods).hasSize(1)
        Assertions.assertThat(loadClass("OrderApi_ControllerModule").methods).hasSize(1)
    }
}
