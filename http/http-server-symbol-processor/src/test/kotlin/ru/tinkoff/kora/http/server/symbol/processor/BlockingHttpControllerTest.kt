package ru.tinkoff.kora.http.server.symbol.processor

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper
import java.util.concurrent.ForkJoinPool

class BlockingHttpControllerTest : AbstractHttpControllerTest() {
    private val executor: BlockingRequestExecutor = BlockingRequestExecutor.Default(ForkJoinPool.commonPool())

    @Test
    fun testReturnBlockingResponse() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", executor)
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnBlockingResponseWithQueryParameter() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(@Query queryParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200)
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", executor)
        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "GET", "/test?queryParameter")
            .hasStatus(400)
            .hasBody("Query parameter 'queryParameter' is required")
    }

    @Test
    fun testReturnBlockingResponseWithRequestParameter() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(bodyParameter: String): HttpServerResponse {
                    return HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter))
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", stringRequestMapper(), executor)
        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnBlockingVoid() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test() {
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", executor)
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
    }

    @Test
    fun testReturnBlockingObject() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): String {
                    return "test"
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", strResponseMapper(), executor)
        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test")
    }

    @Test
    fun testReturnBlockingResponseEntityObject() {
        val module = this.compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "GET", path = "/test")
                fun test(): HttpServerResponseEntity<String> {
                    return HttpServerResponseEntity(403, "test", HttpHeaders.of("test-header", "test-value"))
                }
            }
            
            """.trimIndent())
        val handler: HttpServerRequestHandler = module.getHandler("get_test", HttpServerResponseEntityMapper(strResponseMapper()), executor)
        assertThat(handler, "GET", "/test")
            .hasStatus(403)
            .hasBody("test")
            .hasHeader("test-header", "test-value")
    }

    @Test
    fun testWithInterceptor() {
        val module = this.compile("""
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
        val handler: HttpServerRequestHandler = module.getHandler("get_test", executor, new("TestInterceptor1"), new("TestInterceptor2"))
        assertThat(handler, "GET", "/test?test")
            .hasStatus(200)
            .hasBody(ByteArray(0))
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(ByteArray(0))
    }

}
