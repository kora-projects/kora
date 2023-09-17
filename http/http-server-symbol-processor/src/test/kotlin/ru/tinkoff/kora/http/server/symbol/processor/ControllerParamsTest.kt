package ru.tinkoff.kora.http.server.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ControllerParamsTest : AbstractHttpControllerTest() {
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

    private fun <T> Class<T>.verifyNoDependencies() {
        this.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(1)
        }
    }
}

