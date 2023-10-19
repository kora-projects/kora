package ru.tinkoff.kora.http.server.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.http.common.header.HttpHeaders
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader
import java.util.concurrent.CompletionStage
import java.util.concurrent.ForkJoinPool
import kotlin.reflect.KClass

class ControllerParamsTest : AbstractHttpControllerTest() {
    private val executor: BlockingRequestExecutor = BlockingRequestExecutor.Default(ForkJoinPool.commonPool())

    @Test
    fun testHeader() {
        compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/headerString")
                suspend fun headerString(@Header(value = "string-header") string: String) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableString")
                suspend fun headerNullableString(@Header string: String?) {
                }
                     
                @HttpRoute(method = GET, path = "/headerStringList")
                suspend fun headerNullableString(@Header string: List<String>) {
                }
                        
                @HttpRoute(method = GET, path = "/headerInteger")
                suspend fun headerInteger(@Header(value = "integer-header") integer: Int) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableInteger")
                suspend fun headerNullableInteger(@Header(value = "integer-header") integer: Int?) {
                }

                @HttpRoute(method = GET, path = "/headerIntegerList")
                suspend fun headerStringList(@Header(value = "integer-header") integers: List<Int>) {
                }
            }
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testQuery() {
        compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/queryString")
                suspend fun queryString(@Query("value") value1: String) {
                }
            
                @HttpRoute(method = GET, path = "/queryNullableString")
                suspend fun queryNullableString(@Query value: String?) {
                }
            
                @HttpRoute(method = GET, path = "/queryStringList")
                suspend fun queryStringList(@Query value: List<String>) {
                }

                @HttpRoute(method = GET, path = "/queryInteger")
                suspend fun queryInteger(@Query value: Int) {
                }

                @HttpRoute(method = GET, path = "/queryNullableInteger")
                suspend fun queryNullableInteger(@Query value: Int?) {
                }
            
                @HttpRoute(method = GET, path = "/queryIntegerList")
                suspend fun queryIntegerList(@Query value: List<Int>) {
                }

                @HttpRoute(method = GET, path = "/queryLong")
                suspend fun queryLong(@Query value: Long) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalLong")
                suspend fun queryOptionalLong(@Query value: Long?) {
                }
            
                @HttpRoute(method = GET, path = "/queryLongList")
                suspend fun queryLongList(@Query value: List<Long>) {
                }

                @HttpRoute(method = GET, path = "/queryDouble")
                suspend fun queryDouble(@Query value: Double) {
                }

                @HttpRoute(method = GET, path = "/queryNullableDouble")
                suspend fun queryNullableDouble(@Query value: Double?) {
                }
            
                @HttpRoute(method = GET, path = "/queryDoubleList")
                suspend fun queryDoubleList(@Query value: List<Double>) {
                }
            
                @HttpRoute(method = GET, path = "/queryBoolean")
                suspend fun queryBoolean(@Query value: Boolean) {
                }
            
                @HttpRoute(method = GET, path = "/queryNullableBoolean")
                suspend fun queryNullableBoolean(@Query value: Boolean?) {
                }
            
                @HttpRoute(method = GET, path = "/queryBooleanList")
                suspend fun queryBooleanList(@Query value: List<Boolean>) {
                }
            }
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testHeaders() {
        compile("""
            @HttpController
            class Controller {

                @HttpRoute(method = GET, path = "/headerString")
                suspend fun headerString(@Header(value = "string-header") string: String) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableString")
                suspend fun headerNullableString(@Header string: String?) {
                }
                        
                @HttpRoute(method = GET, path = "/headerStringList")
                suspend fun headerNullableString(@Header string: List<String>) {
                }
                        
                @HttpRoute(method = GET, path = "/headerInteger")
                suspend fun headerInteger(@Header(value = "integer-header") integer: Int) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableInteger")
                suspend fun headerNullableInteger(@Header(value = "integer-header") integer: Int?) {
                }
                        
                @HttpRoute(method = GET, path = "/headerIntegerList")
                suspend fun headerStringList(@Header(value = "integer-header") integers: List<Int>) {
                }
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testCookies() {
        compile("""
            @HttpController
            class Controller {

                @HttpRoute(method = GET, path = "/cookieString")
                suspend fun headerString(@Cookie(value = "someCookie") string: String) {}

                @HttpRoute(method = GET, path = "/cookieNullableString")
                suspend fun headerNullableString(@Cookie string: String?) {}

                @HttpRoute(method = GET, path = "/cookieCookie")
                suspend fun headerInteger(@Cookie cookie: ru.tinkoff.kora.http.common.cookie.Cookie) {}

                @HttpRoute(method = GET, path = "/cookieNullableCookie")
                suspend fun headerNullableInteger(@Cookie cookie: ru.tinkoff.kora.http.common.cookie.Cookie?) {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testContext() {
        val m = compile("""
            import ru.tinkoff.kora.common.Context;
                        
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/ctx")
                suspend fun context(ctx: Context) {}
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testRequest() {
        val m = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                suspend fun request(request: HttpServerRequest) {
                }
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testMappedRequestSuspend() {
        val m = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                suspend fun request(request: String) {
                }
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(
            HttpServerRequestMapper::class.ref(
                CompletionStage::class.ref(
                    String::class
                )
            )
        )
    }

    @Test
    fun testMappedRequest() {
        val m = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(request: String) {
                }
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(3)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(
            HttpServerRequestMapper::class.ref(
                String::class
            )
        )
        Assertions.assertThat(componentMethod.genericParameterTypes[2]).isEqualTo(BlockingRequestExecutor::class.java)
    }

    @Test
    fun testMappedRequestWithMappingSuspend() {
        val m = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                suspend fun request(@ru.tinkoff.kora.common.Mapping(Mapper::class) request: String) {
                }
            }
            """.trimIndent(), """
            class Mapper : ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper<CompletionStage<String>> {
               override fun apply(request: HttpServerRequest) : CompletionStage<String> {
                  return CompletableFuture.completedFuture(request.toString())
               }
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(loadClass("Mapper"))
    }

    @Test
    fun testMappedRequestWithMapping() {
        val m = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(@ru.tinkoff.kora.common.Mapping(Mapper::class) request: String) {
                }
            }
            """.trimIndent(), """
            class Mapper : ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper<String> {
               override fun apply(request: HttpServerRequest) : String {
                  return request.toString()
               }
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(3)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(loadClass("Mapper"))
    }


    @Test
    fun testParseHeaderException() {
        val module = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/test")
                fun test(@Header(value = "some-header") string: Any) {
                }
            }
            """.trimIndent());
        compileResult.assertSuccess();
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_test", parser, executor);

        assertThat(handler, request("GET", "/test", "", HttpHeaders.of("some-header", "test")))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParseQueryException() {
        val module = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/test")
                fun test(@Query(value = "q") string: Any) {
                }
            }
            """.trimIndent());
        compileResult.assertSuccess();
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_test", parser, executor);

        assertThat(handler, request("GET", "/test?q=test", ""))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParsePathException() {
        val module = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/{string}/test")
                fun test(@Path string: Any) {
                }
            }
            """.trimIndent());
        compileResult.assertSuccess();
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_string_test", parser, executor);

        assertThat(handler, request("GET", "/test/test", "").apply { pathParams()["string"] = "test" })
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParseBodySuspendException() {
        val module = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "POST", path = "/test")
                suspend fun test(string: Any) {
                }
            }
            """.trimIndent());
        compileResult.assertSuccess();
        val parser = HttpServerRequestMapper { throw RuntimeException("test-error") }

        val handler = module.getHandler("post_test", parser);

        val rq = request("GET", "/test/test", "");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }


    @Test
    fun testParseBodyException() {
        val module = compile("""
            @HttpController
            class Controller {
                @HttpRoute(method = "POST", path = "/test")
                fun test(string: Any) {
                }
            }
            """.trimIndent());
        compileResult.assertSuccess();
        val parser = HttpServerRequestMapper { throw RuntimeException("test-error") }

        val handler = module.getHandler("post_test", parser, executor);

        val rq = request("GET", "/test/test", "");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }



    private fun <T> Class<T>.verifyNoDependencies() {
        this.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(1)
        }
    }

    private fun KClass<*>.ref(vararg args: KClass<*>): TypeRef<*> {
        val types = args.map { it.java }.toTypedArray()
        return TypeRef.of(this.java, *types)
    }

    private fun KClass<*>.ref(vararg args: TypeRef<*>): TypeRef<*> {
        return TypeRef.of(this.java, *args)
    }

    private fun <T : Any> KClass<T>.ref(): TypeRef<T> {
        return TypeRef.of(this.java)
    }

}

