package ru.tinkoff.kora.http.server.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper

class SuspendHttpControllerTest : AbstractHttpControllerTest() {
    @Test
    fun testReturnSuspendResponse() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnSuspendResponseWithQueryParameter() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(@Query queryParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "GET", "/test?queryParameter")
            .hasStatus(400)
            .hasBody("Query parameter 'queryParameter' is required")
    }

    @Test
    fun testReturnSuspendResponseWithRequestParameter() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(bodyParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter))
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", asyncStringRequestMapper())
        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnSuspendVoid() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test() {
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test")
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnSuspendObject() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(): String {
                    return "test"
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", strResponseMapper())
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnSuspendResponseEntityObject() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                suspend fun test(): HttpServerResponseEntity<String> {
                    return HttpServerResponseEntity(403, "test", HttpHeaders.of("test-header", "test-value"))
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", HttpServerResponseEntityMapper(strResponseMapper()))
        assertThat(handler, "GET", "/test")
            .hasStatus(403)
            .hasBody("test")
            .hasHeader("test-header", "test-value")
    }

    @Test
    suspend fun testWithInterceptor() {
        val module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1::class)
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2::class)
                suspend fun test(): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent(), """
            class TestInterceptor1 : HttpServerInterceptor {
                override fun intercept(context: Context, request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : CompletionStage<HttpServerResponse> {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
                }
            }
            
            """.trimIndent(), """
            class TestInterceptor2 : HttpServerInterceptor {
                override fun intercept(context: Context, request: HttpServerRequest, chain: HttpServerInterceptor.InterceptChain) : CompletionStage<HttpServerResponse> {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", new("TestInterceptor1"), new("TestInterceptor2"))
        assertThat(handler, "GET", "/test?test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(ByteArray(0))
    }

}
