package io.koraframework.http.server.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.TypeRef
import io.koraframework.common.annotation.Tag
import io.koraframework.http.common.header.HttpHeaders
import io.koraframework.http.server.common.request.HttpServerRequestMapper
import io.koraframework.http.server.common.request.HttpServerParameterReader
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.full.functions

class ControllerParamsTest : AbstractHttpControllerTest() {

    @Test
    fun testPath() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/pathString/{valueSome}")
                fun pathString(@Path(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/pathInteger/{value}")
                fun pathInteger(@Path value: Int) { }
            
                @HttpRoute(method = GET, path = "/pathLong/{value}")
                fun pathLong(@Path value: Long) { }
            
                @HttpRoute(method = GET, path = "/pathDouble/{value}")
                fun pathDouble(@Path value: Double) { }
            
                @HttpRoute(method = GET, path = "/pathUUID/{value}")
                fun pathUUID(@Path value: UUID) { }
            
                @HttpRoute(method = GET, path = "/pathBoolean/{value}")
                fun pathBoolean(@Path value: Boolean) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testHeader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/headerString")
                fun headerString(@Header(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/headerStringNullable")
                fun headerStringNullable(@Header value: String?) { }
            
                @HttpRoute(method = GET, path = "/headerStringList")
                fun headerStringList(@Header values: List<String>) { }
            
                @HttpRoute(method = GET, path = "/headerStringListNullable")
                fun headerStringListNullable(@Header values: List<String>?) { }
            
                @HttpRoute(method = GET, path = "/headerStringSet")
                fun headerStringSet(@Header values: Set<String>) { }
            
                @HttpRoute(method = GET, path = "/headerStringSetNullable")
                fun headerStringSetNullable(@Header values: Set<String>?) { }
            
                @HttpRoute(method = GET, path = "/headerInteger")
                fun headerInteger(@Header(value = "valueSome") value: Int) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerNullable")
                fun headerIntegerNullable(@Header value: Int?) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerList")
                fun headerIntegerList(@Header values: List<Int>) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerListNullable")
                fun headerIntegerListNullable(@Header values: List<Int>?) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSet")
                fun headerIntegerSet(@Header values: Set<Int>) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSetNullable")
                fun headerIntegerSetNullable(@Header values: Set<Int>?) { }
            
                @HttpRoute(method = GET, path = "/headerLong")
                fun headerLong(@Header("valueSome") value: Long) { }
            
                @HttpRoute(method = GET, path = "/headerLongNullable")
                fun headerLongNullable(@Header value: Long?) { }
            
                @HttpRoute(method = GET, path = "/headerLongList")
                fun headerLongList(@Header values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongListNullable")
                fun headerLongListNullable(@Header values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongSet")
                fun headerLongSet(@Header values: Set<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongSetNullable")
                fun headerLongSetNullable(@Header values: Set<Long>?) { }
            
                @HttpRoute(method = GET, path = "/headerDouble")
                fun headerDouble(@Header("valueSome") value: Double) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleNullable")
                fun headerDoubleNullable(@Header value: Double?) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleList")
                fun headerDoubleList(@Header values: List<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleListNullable")
                fun headerDoubleListNullable(@Header values: List<Double>?) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSet")
                fun headerDoubleSet(@Header values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSetNullable")
                fun headerDoubleSetNullable(@Header values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerUUID")
                fun headerUUID(@Header("valueSome") value: UUID) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDNullable")
                fun headerUUIDNullable(@Header value: UUID?) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDList")
                fun headerUUIDList(@Header values: List<UUID>) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDListNullable")
                fun headerUUIDListNullable(@Header values: List<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSet")
                fun headerUUIDSet(@Header values: Set<UUID>) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSetNullable")
                fun headerUUIDSetNullable(@Header values: Set<UUID>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testHeaderCustomStringReader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/headerBigInteger")
                fun headerBigInteger(@Header(value = "valueSome") value: BigInteger) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerNullable")
                fun headerBigIntegerNullable(@Header value: BigInteger?) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerList")
                fun headerBigIntegerList(@Header values: List<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerListNullable")
                fun headerBigIntegerListNullable(@Header values: List<BigInteger>?) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSet")
                fun headerBigIntegerSet(@Header values: Set<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSetNullable")
                fun headerBigIntegerSetNullable(@Header values: Set<BigInteger>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(HttpServerParameterReader::class.java)
            val type = it.parameters[1].parameterizedType as ParameterizedType
            Assertions.assertThat(type.actualTypeArguments[0].typeName).endsWith("BigInteger")
        }
    }

    @Test
    fun testQuery() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/queryString")
                fun queryString(@Query(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/queryStringNullable")
                fun queryStringNullable(@Query value: String?) { }
            
                @HttpRoute(method = GET, path = "/queryStringList")
                fun queryStringList(@Query values: List<String>) { }
            
                @HttpRoute(method = GET, path = "/queryStringListNullable")
                fun queryStringListNullable(@Query values: List<String>?) { }
            
                @HttpRoute(method = GET, path = "/queryStringSet")
                fun queryStringSet(@Query values: Set<String>) { }
            
                @HttpRoute(method = GET, path = "/queryStringSetNullable")
                fun queryStringSetNullable(@Query values: Set<String>?) { }
            
                @HttpRoute(method = GET, path = "/queryInteger")
                fun queryInteger(@Query(value = "valueSome") value: Int) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerNullable")
                fun queryIntegerNullable(@Query value: Int?) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerList")
                fun queryIntegerList(@Query values: List<Int>) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerListNullable")
                fun queryIntegerListNullable(@Query values: List<Int>?) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSet")
                fun queryIntegerSet(@Query values: Set<Int>) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSetNullable")
                fun queryIntegerSetNullable(@Query values: Set<Int>?) { }
            
                @HttpRoute(method = GET, path = "/queryLong")
                fun queryLong(@Query("valueSome") value: Long) { }
            
                @HttpRoute(method = GET, path = "/queryLongNullable")
                fun queryLongNullable(@Query value: Long?) { }
            
                @HttpRoute(method = GET, path = "/queryLongList")
                fun queryLongList(@Query values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongListNullable")
                fun queryLongListNullable(@Query values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongSet")
                fun queryLongSet(@Query values: Set<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongSetNullable")
                fun queryLongSetNullable(@Query values: Set<Long>?) { }
            
                @HttpRoute(method = GET, path = "/queryDouble")
                fun queryDouble(@Query("valueSome") value: Double) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleNullable")
                fun queryDoubleNullable(@Query value: Double?) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleList")
                fun queryDoubleList(@Query values: List<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleListNullable")
                fun queryDoubleListNullable(@Query values: List<Double>?) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSet")
                fun queryDoubleSet(@Query values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSetNullable")
                fun queryDoubleSetNullable(@Query values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryUUID")
                fun queryUUID(@Query("valueSome") value: UUID) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDNullable")
                fun queryUUIDNullable(@Query value: UUID?) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDList")
                fun queryUUIDList(@Query values: List<UUID>) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDListNullable")
                fun queryUUIDListNullable(@Query values: List<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSet")
                fun queryUUIDSet(@Query values: Set<UUID>) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSetNullable")
                fun queryUUIDSetNullable(@Query values: Set<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/queryBoolean")
                fun queryBoolean(@Query("valueSome") value: Boolean) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanNullable")
                fun queryBooleanNullable(@Query value: Boolean?) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanList")
                fun queryBooleanList(@Query values: List<Boolean>) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanListNullable")
                fun queryBooleanListNullable(@Query values: List<Boolean>?) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSet")
                fun queryBooleanSet(@Query values: Set<Boolean>) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSetNullable")
                fun queryBooleanSetNullable(@Query values: Set<Boolean>?) { }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testQueryCustomStringReader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/queryBigInteger")
                fun queryBigInteger(@Query(value = "valueSome") value: BigInteger) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerNullable")
                fun queryBigIntegerNullable(@Query value: BigInteger?) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerList")
                fun queryBigIntegerList(@Query values: List<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerListNullable")
                fun queryBigIntegerListNullable(@Query values: List<BigInteger>?) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSet")
                fun queryBigIntegerSet(@Query values: Set<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSetNullable")
                fun queryBigIntegerSetNullable(@Query values: Set<BigInteger>?) { }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(HttpServerParameterReader::class.java)
            val type = it.parameters[1].parameterizedType as ParameterizedType
            Assertions.assertThat(type.actualTypeArguments[0].typeName).endsWith("BigInteger")
        }
    }

    @Test
    fun testQueryEnum() {
        compile(
            """
            @HttpController
            class Controller {
            
                enum class TestEnum {
                    VAL1, VAL2
                }
            
                @HttpRoute(method = GET, path = "/queryEnum")
                fun queryEnum(@Query("value") value1: TestEnum) { }
            
                @HttpRoute(method = GET, path = "/queryNullableEnum")
                fun queryNullableEnum(@Query value: TestEnum?) { }

                @HttpRoute(method = GET, path = "/queryEnumList")
                fun queryEnumList(@Query value: List<TestEnum>) { }

                @HttpRoute(method = GET, path = "/queryNullableEnumList")
                fun queryNullableEnumList(@Query value: List<TestEnum>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(HttpServerParameterReader::class.java)
            val type = it.parameters[1].parameterizedType as ParameterizedType
            Assertions.assertThat(type.actualTypeArguments[0].typeName).endsWith("TestEnum")
        }
    }

    @Test
    fun testHeaderEnum() {
        compile(
            """
            @HttpController
            class Controller {
            
                enum class TestEnum {
                    VAL1, VAL2
                }
            
                @HttpRoute(method = GET, path = "/headerEnum")
                fun queryEnum(@Header("value") value1: TestEnum) { }
            
                @HttpRoute(method = GET, path = "/headerNullableEnum")
                fun queryNullableEnum(@Header("value") value: TestEnum?) { }

                @HttpRoute(method = GET, path = "/headerEnumList")
                fun queryEnumList(@Header("value") value: List<TestEnum>) { }

                @HttpRoute(method = GET, path = "/headerNullableEnumList")
                fun queryNullableEnumList(@Header("value") value: List<TestEnum>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(HttpServerParameterReader::class.java)
            val type = it.parameters[1].parameterizedType as ParameterizedType
            Assertions.assertThat(type.actualTypeArguments[0].typeName).endsWith("TestEnum")
        }
    }

    @Test
    fun testHeaders() {
        compile(
            """
            @HttpController
            class Controller {

                @HttpRoute(method = GET, path = "/headerString")
                fun headerString(@Header(value = "string-header") string: String) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableString")
                fun headerNullableString(@Header string: String?) {
                }
                        
                @HttpRoute(method = GET, path = "/headerStringList")
                fun headerNullableString(@Header string: List<String>) {
                }
                        
                @HttpRoute(method = GET, path = "/headerInteger")
                fun headerInteger(@Header(value = "integer-header") integer: Int) {
                }
                        
                @HttpRoute(method = GET, path = "/headerNullableInteger")
                fun headerNullableInteger(@Header(value = "integer-header") integer: Int?) {
                }
                        
                @HttpRoute(method = GET, path = "/headerIntegerList")
                fun headerStringList(@Header(value = "integer-header") integers: List<Int>) {
                }
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testCookies() {
        compile(
            """
            @HttpController
            class Controller {

                @HttpRoute(method = GET, path = "/cookieString")
                fun headerString(@Cookie(value = "someCookie") string: String) {}

                @HttpRoute(method = GET, path = "/cookieNullableString")
                fun headerNullableString(@Cookie string: String?) {}

                @HttpRoute(method = GET, path = "/cookieCookie")
                fun headerInteger(@Cookie cookie: io.koraframework.http.common.cookie.Cookie) {}

                @HttpRoute(method = GET, path = "/cookieNullableCookie")
                fun headerNullableInteger(@Cookie cookie: io.koraframework.http.common.cookie.Cookie?) {}
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testRequest() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(request: HttpServerRequest) {
                }
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testMappedRequestSuspend() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(request: String) {
                }
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(
            HttpServerRequestMapper::class.ref(
                String::class
            )
        )
    }

    @Test
    fun testMappedRequest() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(request: String) {
                }
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(
            HttpServerRequestMapper::class.ref(
                String::class
            )
        )
    }

    @Test
    fun testMappedRequestWithMappingSuspend() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(@io.koraframework.common.annotation.Mapping(Mapper::class) request: String) {
                }
            }
            """.trimIndent(), """
            class Mapper : io.koraframework.http.server.common.request.HttpServerRequestMapper<CompletionStage<String>> {
               override fun apply(request: HttpServerRequest) : CompletionStage<String> {
                  return CompletableFuture.completedFuture(request.toString())
               }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(loadClass("Mapper"))
    }

    @Test
    fun testMappedRequestWithMapping() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                fun request(@io.koraframework.common.annotation.Mapping(Mapper::class) request: String) {
                }
            }
            """.trimIndent(), """
            class Mapper : io.koraframework.http.server.common.request.HttpServerRequestMapper<String> {
               override fun apply(request: HttpServerRequest) : String {
                  return request.toString()
               }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(2)
        Assertions.assertThat(componentMethod.genericParameterTypes[1]).isEqualTo(loadClass("Mapper"))
    }


    @Test
    fun testParseHeaderException() {
        val module = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/test")
                fun test(@Header(value = "some-header") string: Any) {
                }
            }
            """.trimIndent()
        );
        compileResult.assertSuccess();
        val parser = HttpServerParameterReader<Any> {
            throw RuntimeException("test-error")
        }

        val handler = module.getHandler("get_test", parser);

        assertThat(handler, request("GET", "/test", "", HttpHeaders.of("some-header", "test")))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParseQueryException() {
        val module = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/test")
                fun test(@Query(value = "q") string: Any) {
                }
            }
            """.trimIndent()
        );
        compileResult.assertSuccess();
        val parser = HttpServerParameterReader<Any> {
            throw RuntimeException("test-error")
        }

        val handler = module.getHandler("get_test", parser);

        assertThat(handler, request("GET", "/test?q=test", ""))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParsePathException() {
        val module = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/{string}/test")
                fun test(@Path string: Any) {
                }
            }
            """.trimIndent()
        );
        compileResult.assertSuccess();
        val parser = HttpServerParameterReader<Any> {
            throw RuntimeException("test-error")
        }

        val handler = module.getHandler("get_string_test", parser);

        assertThat(handler, request("GET", "/test/test", "").apply { pathParams()["string"] = "test" })
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testParseBodySuspendException() {
        val module = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "POST", path = "/test")
                fun test(string: Any) {
                }
            }
            """.trimIndent()
        );
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
        val module = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = "POST", path = "/test")
                fun test(string: Any) {
                }
            }
            """.trimIndent()
        );
        compileResult.assertSuccess();
        val parser = HttpServerRequestMapper { throw RuntimeException("test-error") }

        val handler = module.getHandler("post_test", parser);

        val rq = request("GET", "/test/test", "");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    fun testControllerTag() {
        compile(
            """
            @Tag(String::class)
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/pathString/{valueSome}")
                fun pathString(@Path(value = "valueSome") value: String) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val module = loadClass("ControllerModule")
        module.verifyNoDependencies()
        val controller = loadClass("Controller")
        Assertions.assertThat(controller.kotlin.annotations.first()).isInstanceOf(Tag::class.java)
        Assertions.assertThat(module.kotlin.functions.first().annotations.first()).isInstanceOf(Tag::class.java)
        Assertions.assertThat(module.kotlin.functions.first().parameters.last().annotations.first()).isInstanceOf(Tag::class.java)
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

