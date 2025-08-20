package ru.tinkoff.kora.http.server.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.common.header.HttpHeaders
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader
import java.lang.reflect.ParameterizedType
import java.util.concurrent.CompletionStage
import java.util.concurrent.ForkJoinPool
import kotlin.reflect.KClass
import kotlin.reflect.full.functions

class ControllerParamsTest : AbstractHttpControllerTest() {

    private val executor: BlockingRequestExecutor = BlockingRequestExecutor.Default(ForkJoinPool.commonPool())

    @Test
    fun testPath() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/pathString/{valueSome}")
                suspend fun pathString(@Path(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/pathInteger/{value}")
                suspend fun pathInteger(@Path value: Int) { }
            
                @HttpRoute(method = GET, path = "/pathLong/{value}")
                suspend fun pathLong(@Path value: Long) { }
            
                @HttpRoute(method = GET, path = "/pathDouble/{value}")
                suspend fun pathDouble(@Path value: Double) { }
            
                @HttpRoute(method = GET, path = "/pathUUID/{value}")
                suspend fun pathUUID(@Path value: UUID) { }
            
                @HttpRoute(method = GET, path = "/pathBoolean/{value}")
                suspend fun pathBoolean(@Path value: Boolean) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testHeader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/headerString")
                suspend fun headerString(@Header(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/headerStringNullable")
                suspend fun headerStringNullable(@Header value: String?) { }
            
                @HttpRoute(method = GET, path = "/headerStringList")
                suspend fun headerStringList(@Header values: List<String>) { }
            
                @HttpRoute(method = GET, path = "/headerStringListNullable")
                suspend fun headerStringListNullable(@Header values: List<String>?) { }
            
                @HttpRoute(method = GET, path = "/headerStringSet")
                suspend fun headerStringSet(@Header values: Set<String>) { }
            
                @HttpRoute(method = GET, path = "/headerStringSetNullable")
                suspend fun headerStringSetNullable(@Header values: Set<String>?) { }
            
                @HttpRoute(method = GET, path = "/headerInteger")
                suspend fun headerInteger(@Header(value = "valueSome") value: Int) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerNullable")
                suspend fun headerIntegerNullable(@Header value: Int?) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerList")
                suspend fun headerIntegerList(@Header values: List<Int>) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerListNullable")
                suspend fun headerIntegerListNullable(@Header values: List<Int>?) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSet")
                suspend fun headerIntegerSet(@Header values: Set<Int>) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSetNullable")
                suspend fun headerIntegerSetNullable(@Header values: Set<Int>?) { }
            
                @HttpRoute(method = GET, path = "/headerLong")
                suspend fun headerLong(@Header("valueSome") value: Long) { }
            
                @HttpRoute(method = GET, path = "/headerLongNullable")
                suspend fun headerLongNullable(@Header value: Long?) { }
            
                @HttpRoute(method = GET, path = "/headerLongList")
                suspend fun headerLongList(@Header values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongListNullable")
                suspend fun headerLongListNullable(@Header values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongSet")
                suspend fun headerLongSet(@Header values: Set<Long>) { }
            
                @HttpRoute(method = GET, path = "/headerLongSetNullable")
                suspend fun headerLongSetNullable(@Header values: Set<Long>?) { }
            
                @HttpRoute(method = GET, path = "/headerDouble")
                suspend fun headerDouble(@Header("valueSome") value: Double) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleNullable")
                suspend fun headerDoubleNullable(@Header value: Double?) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleList")
                suspend fun headerDoubleList(@Header values: List<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleListNullable")
                suspend fun headerDoubleListNullable(@Header values: List<Double>?) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSet")
                suspend fun headerDoubleSet(@Header values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSetNullable")
                suspend fun headerDoubleSetNullable(@Header values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/headerUUID")
                suspend fun headerUUID(@Header("valueSome") value: UUID) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDNullable")
                suspend fun headerUUIDNullable(@Header value: UUID?) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDList")
                suspend fun headerUUIDList(@Header values: List<UUID>) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDListNullable")
                suspend fun headerUUIDListNullable(@Header values: List<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSet")
                suspend fun headerUUIDSet(@Header values: Set<UUID>) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSetNullable")
                suspend fun headerUUIDSetNullable(@Header values: Set<UUID>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testHeaderCustomStringReader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/headerBigInteger")
                suspend fun headerBigInteger(@Header(value = "valueSome") value: BigInteger) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerNullable")
                suspend fun headerBigIntegerNullable(@Header value: BigInteger?) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerList")
                suspend fun headerBigIntegerList(@Header values: List<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerListNullable")
                suspend fun headerBigIntegerListNullable(@Header values: List<BigInteger>?) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSet")
                suspend fun headerBigIntegerSet(@Header values: Set<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSetNullable")
                suspend fun headerBigIntegerSetNullable(@Header values: Set<BigInteger>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(StringParameterReader::class.java)
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
                suspend fun queryString(@Query(value = "valueSome") value: String) { }
            
                @HttpRoute(method = GET, path = "/queryStringNullable")
                suspend fun queryStringNullable(@Query value: String?) { }
            
                @HttpRoute(method = GET, path = "/queryStringList")
                suspend fun queryStringList(@Query values: List<String>) { }
            
                @HttpRoute(method = GET, path = "/queryStringListNullable")
                suspend fun queryStringListNullable(@Query values: List<String>?) { }
            
                @HttpRoute(method = GET, path = "/queryStringSet")
                suspend fun queryStringSet(@Query values: Set<String>) { }
            
                @HttpRoute(method = GET, path = "/queryStringSetNullable")
                suspend fun queryStringSetNullable(@Query values: Set<String>?) { }
            
                @HttpRoute(method = GET, path = "/queryInteger")
                suspend fun queryInteger(@Query(value = "valueSome") value: Int) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerNullable")
                suspend fun queryIntegerNullable(@Query value: Int?) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerList")
                suspend fun queryIntegerList(@Query values: List<Int>) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerListNullable")
                suspend fun queryIntegerListNullable(@Query values: List<Int>?) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSet")
                suspend fun queryIntegerSet(@Query values: Set<Int>) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSetNullable")
                suspend fun queryIntegerSetNullable(@Query values: Set<Int>?) { }
            
                @HttpRoute(method = GET, path = "/queryLong")
                suspend fun queryLong(@Query("valueSome") value: Long) { }
            
                @HttpRoute(method = GET, path = "/queryLongNullable")
                suspend fun queryLongNullable(@Query value: Long?) { }
            
                @HttpRoute(method = GET, path = "/queryLongList")
                suspend fun queryLongList(@Query values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongListNullable")
                suspend fun queryLongListNullable(@Query values: List<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongSet")
                suspend fun queryLongSet(@Query values: Set<Long>) { }
            
                @HttpRoute(method = GET, path = "/queryLongSetNullable")
                suspend fun queryLongSetNullable(@Query values: Set<Long>?) { }
            
                @HttpRoute(method = GET, path = "/queryDouble")
                suspend fun queryDouble(@Query("valueSome") value: Double) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleNullable")
                suspend fun queryDoubleNullable(@Query value: Double?) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleList")
                suspend fun queryDoubleList(@Query values: List<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleListNullable")
                suspend fun queryDoubleListNullable(@Query values: List<Double>?) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSet")
                suspend fun queryDoubleSet(@Query values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSetNullable")
                suspend fun queryDoubleSetNullable(@Query values: Set<Double>) { }
            
                @HttpRoute(method = GET, path = "/queryUUID")
                suspend fun queryUUID(@Query("valueSome") value: UUID) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDNullable")
                suspend fun queryUUIDNullable(@Query value: UUID?) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDList")
                suspend fun queryUUIDList(@Query values: List<UUID>) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDListNullable")
                suspend fun queryUUIDListNullable(@Query values: List<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSet")
                suspend fun queryUUIDSet(@Query values: Set<UUID>) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSetNullable")
                suspend fun queryUUIDSetNullable(@Query values: Set<UUID>?) { }
            
                @HttpRoute(method = GET, path = "/queryBoolean")
                suspend fun queryBoolean(@Query("valueSome") value: Boolean) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanNullable")
                suspend fun queryBooleanNullable(@Query value: Boolean?) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanList")
                suspend fun queryBooleanList(@Query values: List<Boolean>) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanListNullable")
                suspend fun queryBooleanListNullable(@Query values: List<Boolean>?) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSet")
                suspend fun queryBooleanSet(@Query values: Set<Boolean>) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSetNullable")
                suspend fun queryBooleanSetNullable(@Query values: Set<Boolean>?) { }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testQueryCustomStringReader() {
        compile(
            """
            @HttpController
            class Controller {
            
                @HttpRoute(method = GET, path = "/queryBigInteger")
                suspend fun queryBigInteger(@Query(value = "valueSome") value: BigInteger) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerNullable")
                suspend fun queryBigIntegerNullable(@Query value: BigInteger?) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerList")
                suspend fun queryBigIntegerList(@Query values: List<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerListNullable")
                suspend fun queryBigIntegerListNullable(@Query values: List<BigInteger>?) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSet")
                suspend fun queryBigIntegerSet(@Query values: Set<BigInteger>) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSetNullable")
                suspend fun queryBigIntegerSetNullable(@Query values: Set<BigInteger>?) { }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(StringParameterReader::class.java)
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
                suspend fun queryEnum(@Query("value") value1: TestEnum) { }
            
                @HttpRoute(method = GET, path = "/queryNullableEnum")
                suspend fun queryNullableEnum(@Query value: TestEnum?) { }

                @HttpRoute(method = GET, path = "/queryEnumList")
                suspend fun queryEnumList(@Query value: List<TestEnum>) { }

                @HttpRoute(method = GET, path = "/queryNullableEnumList")
                suspend fun queryNullableEnumList(@Query value: List<TestEnum>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(StringParameterReader::class.java)
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
                suspend fun queryEnum(@Header("value") value1: TestEnum) { }
            
                @HttpRoute(method = GET, path = "/headerNullableEnum")
                suspend fun queryNullableEnum(@Header("value") value: TestEnum?) { }

                @HttpRoute(method = GET, path = "/headerEnumList")
                suspend fun queryEnumList(@Header("value") value: List<TestEnum>) { }

                @HttpRoute(method = GET, path = "/headerNullableEnumList")
                suspend fun queryNullableEnumList(@Header("value") value: List<TestEnum>?) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("ControllerModule")
        clazz.methods.forEach {
            Assertions.assertThat(it.parameters).hasSize(2)
            Assertions.assertThat(it.parameters[1].type).isAssignableFrom(StringParameterReader::class.java)
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testCookies() {
        compile(
            """
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testContext() {
        val m = compile(
            """
            import ru.tinkoff.kora.common.Context;
                        
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/ctx")
                suspend fun context(ctx: Context) {}
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testRequest() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                suspend fun request(request: HttpServerRequest) {
                }
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        compileResult.loadClass("ControllerModule").verifyNoDependencies()
    }

    @Test
    fun testMappedRequestSuspend() {
        val m = compile(
            """
            @HttpController
            class Controller {
                @HttpRoute(method = GET, path = "/request")
                suspend fun request(request: String) {
                }
            }
            
            """.trimIndent()
        )
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
        val m = compile(
            """
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
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
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
                fun request(@ru.tinkoff.kora.common.Mapping(Mapper::class) request: String) {
                }
            }
            """.trimIndent(), """
            class Mapper : ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper<String> {
               override fun apply(request: HttpServerRequest) : String {
                  return request.toString()
               }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val componentMethod = compileResult.loadClass("ControllerModule").methods[0]
        Assertions.assertThat(componentMethod.parameters).hasSize(3)
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
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_test", parser, executor);

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
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_test", parser, executor);

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
        val parser = StringParameterReader<Any> { throw RuntimeException("test-error") }

        val handler = module.getHandler("get_string_test", parser, executor);

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
                suspend fun test(string: Any) {
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

        val handler = module.getHandler("post_test", parser, executor);

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
                suspend fun pathString(@Path(value = "valueSome") value: String) { }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val module = compileResult.loadClass("ControllerModule")
        module.verifyNoDependencies()
        val controller = compileResult.loadClass("Controller")
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

