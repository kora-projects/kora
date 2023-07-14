package ru.tinkoff.kora.http.server.symbol.processor

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.server.symbol.processor.controllers.*
import ru.tinkoff.kora.http.server.symbol.processor.server.TestHttpServer
import java.nio.charset.StandardCharsets

@KspExperimental
class HttpControllerProcessorTest {

    @Test
    fun testPathParameters() {
        val server: TestHttpServer<TestControllerPathParameters> = TestHttpServer.fromController(TestControllerPathParameters::class)
        println("a")
        `when`(server.controller.pathString(ArgumentMatchers.anyString())).thenReturn("test response")
        println("b")
        server.invoke(HttpMethod.GET, "/pathString/StringValue", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("test response")
    }

    @Test
    fun testTypes() {
        val server = TestHttpServer.fromController(
            TestControllerWithDifferentTypes::class
        )
        runBlocking {
            `when`(server.controller.deleteByteArrayMonoVoidResult(any<ByteArray>())).thenReturn(Unit)
            server.invoke(HttpMethod.DELETE, "/deleteByteArrayVoidResult", ByteArray(0))
                .verifyStatus(200)
            server.invoke(HttpMethod.DELETE, "/deleteByteArrayMonoVoidResult", ByteArray(0))
                .verifyStatus(200)
        }
    }

    @Test
    fun testHeaderParameters() {
        val server: TestHttpServer<TestControllerHeaderParameters> =
            TestHttpServer.fromController(
                TestControllerHeaderParameters::class
            )
        `when`(server.controller.headerString("someHeaderString")).thenReturn("otherString")
        `when`(server.controller.headerInt(15)).thenReturn(15)

        server.invoke(HttpMethod.GET, "/headerString", ByteArray(0), "string-header" to "someHeaderString")
            .verifyStatus(200)
            .verifyBody("otherString")
        server.invoke(HttpMethod.GET, "/headerString", ByteArray(0))
            .verifyStatus(400)
        server.invoke(HttpMethod.GET, "/headerInt", ByteArray(0))
            .verifyStatus(400)
        server.invoke(HttpMethod.GET, "/headerInt", ByteArray(0), "int-header" to "15")
            .verifyStatus(200)
            .verifyBody("15")
    }

    @Test
    fun testPrefix() {
        val server: TestHttpServer<TestControllerWithPrefix> =
            TestHttpServer.fromController(
                TestControllerWithPrefix::class
            )
        `when`(server.controller.test()).thenReturn("test")
        `when`(server.controller.testRoot()).thenReturn("root")
        server.invoke(HttpMethod.GET, "/root/test", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("test")
        server.invoke(HttpMethod.POST, "/root", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("root")
        server.invoke(HttpMethod.GET, "/test", ByteArray(0))
            .verifyStatus(404)
        server.invoke(HttpMethod.GET, "/root", ByteArray(0))
            .verifyStatus(404)
    }

    @Test
    fun testQueryParameters() {
        val server: TestHttpServer<TestControllerQueryParameters> =
            TestHttpServer.fromController(
                TestControllerQueryParameters::class
            )
    }

    @Test
    fun testMapper() {
        val server: TestHttpServer<TestControllerWithMappers> =
            TestHttpServer.fromController(
                TestControllerWithMappers::class
            )
    }

    @Test
    fun testPaths() {
        val server: TestHttpServer<TestControllerWithPaths> =
            TestHttpServer.fromController(
                TestControllerWithPaths::class
            )
    }

    @Test
    fun testKotlin() {
        runBlocking {
            val server: TestHttpServer<TestControllerKotlinSuspendHandler> =
                TestHttpServer.fromController(
                    TestControllerKotlinSuspendHandler::class
                )
            `when`(server.controller.kotlinNullableHeader(anyOrNull())).thenCallRealMethod()
            server.invoke(HttpMethod.GET, "/kotlinNullableHeader", ByteArray(0), "str" to "someHeaderString")
                .verifyStatus(200)
                .verifyBody("someHeaderString")
            server.invoke(HttpMethod.GET, "/kotlinNullableHeader", ByteArray(0))
                .verifyStatus(200)
                .verifyBody("placeholder")
            `when`(server.controller.kotlinNonNullableHeader(ArgumentMatchers.anyString())).thenCallRealMethod()
            server.invoke(HttpMethod.GET, "/kotlinNonNullableHeader", ByteArray(0))
                .verifyStatus(400)
            `when`(server.controller.kotlinNullableQuery(anyOrNull())).thenCallRealMethod()
            server.invoke(HttpMethod.GET, "/kotlinNullableQuery?str=test", ByteArray(0))
                .verifyStatus(200)
                .verifyBody("test")
            server.invoke(HttpMethod.GET, "/kotlinNullableQuery", ByteArray(0))
                .verifyStatus(200)
                .verifyBody("placeholder")
            `when`<Any>(server.controller.kotlinSuspendHandler(null)).thenCallRealMethod()
            server.invoke(HttpMethod.GET, "/kotlinSuspendHandler", ByteArray(0))
                .verifyStatus(200)
                .verifyBody("44")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testWithParent() {
        val server: TestHttpServer<TestControllerWithInheritance> =
            TestHttpServer.fromController(
                TestControllerWithInheritance::class
            )
        `when`<String>(server.controller.someMethod()).thenReturn("parent")
        server.invoke(HttpMethod.GET, "/base/parent", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("parent")
        `when`(server.controller.someOtherMethod()).thenCallRealMethod()
        server.invoke(HttpMethod.GET, "/base/child", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("child")
        Mockito.doCallRealMethod().`when`(server.controller).someMethodWithParam(ArgumentMatchers.eq("test"))
        server.invoke(HttpMethod.POST, "/base/parent-param", "test".toByteArray(StandardCharsets.UTF_8))
            .verifyStatus(200)
        Mockito.verify(server.controller).someMethodWithParam("test")
    }

    @Test
    fun testMultipleParams() {
        val server: TestHttpServer<MultipleParamsController> =
            TestHttpServer.fromController(
                MultipleParamsController::class
            )
        server.invoke("POST", "/path", ByteArray(0))
            .verifyStatus(400)
            .verifyBody("TEST")
        server.invoke("POST", "/path", ByteArray(0), "test-header" to "val")
            .verifyStatus(200)
    }

    @Test
    fun testNullableResult() {
        val server: TestHttpServer<TestControllerWithNullableResult> =
            TestHttpServer.fromController(
                TestControllerWithNullableResult::class
            )
        server.invoke("GET", "/getNullable", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("null")
    }

    @Test
    fun testResponseEntity() {
        runBlocking {
            val server = TestHttpServer.fromController(TestControllerWithResponseEntity::class)
            `when`(server.controller.test(ArgumentMatchers.anyInt())).thenCallRealMethod()
            `when`(server.controller.suspended(ArgumentMatchers.anyInt())).thenCallRealMethod()
            server.invoke("GET", "/test?code=404", ByteArray(0))
                .verifyStatus(404)
                .verifyBody("404")
            server.invoke("GET", "/test?code=505", ByteArray(0))
                .verifyStatus(505)
                .verifyBody("505")
            server.invoke("GET", "/test2?code=404", ByteArray(0))
                .verifyStatus(404)
                .verifyBody("404")
            server.invoke("GET", "/test2?code=505", ByteArray(0))
                .verifyStatus(505)
                .verifyBody("505")
        }
    }

    @Test
    fun testControllerWithCustomReaders() {
        val server: TestHttpServer<TestControllerWithCustomReaders> =
            TestHttpServer.fromController(
                TestControllerWithCustomReaders::class
            )
        whenever(server.controller.test(any(), anyOrNull(), anyOrNull())).thenCallRealMethod()

        server.invoke("GET", "/test/fourth?queryEntity=first&queryEntity=second&queryEntity=third", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("first, second, third, fourth")
        server.invoke("GET", "/test/first", ByteArray(0))
            .verifyStatus(200)
            .verifyBody("first")
    }


    @Test
    fun testControllerWithInterceptors() {
        val server: TestHttpServer<TestControllerWithInterceptors> = TestHttpServer.fromController(TestControllerWithInterceptors::class)
        whenever(server.controller.withMethodLevelInterceptors()).thenCallRealMethod()
        server.invoke("GET", "/withMethodLevelInterceptors", ByteArray(0))
            .verifyStatus(200)
    }
}



