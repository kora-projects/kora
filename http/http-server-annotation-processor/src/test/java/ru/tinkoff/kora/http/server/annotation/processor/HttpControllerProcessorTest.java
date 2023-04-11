package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.server.annotation.processor.controller.*;
import ru.tinkoff.kora.http.server.annotation.processor.server.TestHttpServer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static ru.tinkoff.kora.http.common.HttpMethod.*;

class HttpControllerProcessorTest {

    @Test
    void testPathParameters() {
        var server = TestHttpServer.fromController(TestControllerPathParameters.class);
        when(server.controller.pathString(any())).thenReturn("test response");

        server.invoke(GET, "/pathString/StringValue", new byte[0])
            .verifyStatus(200)
            .verifyBody("test response");
    }

    @Test
    void testTypes() {
        var server = TestHttpServer.fromController(TestControllerWithDifferentTypes.class);
        when(server.controller.deleteByteArrayMonoVoidResult(any())).thenReturn(Mono.empty());

        server.invoke(DELETE, "/deleteByteArrayVoidResult", new byte[0])
            .verifyStatus(200);
        server.invoke(DELETE, "/deleteByteArrayMonoVoidResult", new byte[0])
            .verifyStatus(200);
    }

    @Test
    void testHeaderParameters() {
        var server = TestHttpServer.fromController(TestControllerHeaderParameters.class);
        when(server.controller.headerString(eq("someHeaderString"))).thenReturn("otherString");

        server.invoke(GET, "/headerString", new byte[0], Map.entry("string-header", "someHeaderString"))
            .verifyStatus(200)
            .verifyBody("otherString");

        server.invoke(GET, "/headerString", new byte[0])
            .verifyStatus(400);
        server.invoke(GET, "", new byte[0], Map.entry("", ""));
    }

    @Test
    void testIntegerHeaderParameters() {
        var server = TestHttpServer.fromController(TestControllerHeaderParameters.class);

        server.invoke(GET, "/headerInteger", new byte[0], Map.entry("integer-header", "5"))
            .verifyStatus(200);
        server.invoke(GET, "/headerInteger", new byte[0], Map.entry("integer-header", "string"))
            .verifyStatus(400);

        server.invoke(GET, "/headerNullableInteger", new byte[0], Map.entry("integer-header", "5"))
            .verifyStatus(200);
        server.invoke(GET, "/headerNullableInteger", new byte[0], Map.entry("integer-header", "string"))
            .verifyStatus(400);
        server.invoke(GET, "/headerNullableInteger", new byte[0])
            .verifyStatus(200);

        server.invoke(GET, "/headerOptionalInteger", new byte[0], Map.entry("integer-header", "5"))
            .verifyStatus(200);
        server.invoke(GET, "/headerOptionalInteger", new byte[0], Map.entry("integer-header", "string"))
            .verifyStatus(400);
        server.invoke(GET, "/headerOptionalInteger", new byte[0])
            .verifyStatus(200);

        server.invoke(GET, "/headerIntegerList", new byte[0], Map.entry("integer-header", "1,2,3,4,5"))
            .verifyStatus(200);
        server.invoke(GET, "/headerIntegerList", new byte[0], Map.entry("integer-header", ""))
            .verifyStatus(200);
        server.invoke(GET, "/headerIntegerList", new byte[0], Map.entry("integer-header", "1,2,,4"))
            .verifyStatus(400);
        server.invoke(GET, "/headerIntegerList", new byte[0], Map.entry("integer-header", "string"))
            .verifyStatus(400);
        server.invoke(GET, "/headerIntegerList", new byte[0])
            .verifyStatus(400);
    }

    @Test
    void testPrefix() {
        var server = TestHttpServer.fromController(TestControllerWithPrefix.class);
        when(server.controller.test()).thenReturn("test");
        when(server.controller.testRoot()).thenReturn("root");

        server.invoke(GET, "/root/test", new byte[0])
            .verifyStatus(200)
            .verifyBody("test");
        server.invoke(POST, "/root", new byte[0])
            .verifyStatus(200)
            .verifyBody("root");
        server.invoke(GET, "/test", new byte[0])
            .verifyStatus(404);
        server.invoke(GET, "/root", new byte[0])
            .verifyStatus(404);
    }

    @Test
    void testQueryParameters() {
        var server = TestHttpServer.fromController(TestControllerQueryParameters.class);

        server.invoke(GET, "/queryEnumList?value=VAL1&value=VAL2", new byte[0])
            .verifyStatus(200)
        ;
        server.invoke(GET, "/queryNullableEnumList", new byte[0])
            .verifyStatus(200)
        ;
        server.invoke(GET, "/queryNullableEnumList?value=VAL1&value=VAL2", new byte[0])
            .verifyStatus(200)
        ;

    }

    @Test
    void testMapper() {
        var server = TestHttpServer.fromController(TestControllerWithMappers.class);
    }

    @Test
    void testPaths() {
        var server = TestHttpServer.fromController(TestControllerWithPaths.class);
    }

    @Test
    void testWithParent() throws Exception {
        var server = TestHttpServer.fromController(TestControllerWithInheritance.class);

        when(server.controller.someMethod()).thenReturn("parent");
        server.invoke(GET, "/base/parent", new byte[0])
            .verifyStatus(200)
            .verifyBody("parent");

        when(server.controller.someOtherMethod()).thenCallRealMethod();
        server.invoke(GET, "/base/child", new byte[0])
            .verifyStatus(200)
            .verifyBody("child");

        doCallRealMethod().when(server.controller).someMethodWithParam(eq("test"));
        server.invoke(POST, "/base/parent-param", "test".getBytes(StandardCharsets.UTF_8))
            .verifyStatus(200);
        verify(server.controller).someMethodWithParam("test");
    }

    @Test
    void testMultipleParams() {
        var server = TestHttpServer.fromController(MultipleParamsController.class);

        server.invoke("POST", "/path", new byte[0])
            .verifyStatus(400)
            .verifyBody("TEST");

        server.invoke("POST", "/path", new byte[0], Map.entry("test-header", "val"))
            .verifyStatus(200);
    }

    @Test
    void testNullableResult() {
        var server = TestHttpServer.fromController(TestControllerWithNullableResult.class);

        server.invoke("GET", "/getNullable", new byte[0])
            .verifyStatus(200)
            .verifyBody("null");
    }

    @Test
    void testResponseEntity() {
        var server = TestHttpServer.fromController(TestControllerWithResponseEntity.class);
        when(server.controller.test(anyInt())).thenCallRealMethod();
        when(server.controller.mono(anyInt())).thenCallRealMethod();

        server.invoke("GET", "/test?code=404", new byte[0])
            .verifyStatus(404)
            .verifyBody("404");
        server.invoke("GET", "/test?code=505", new byte[0])
            .verifyStatus(505)
            .verifyBody("505");
        server.invoke("GET", "/test2?code=404", new byte[0])
            .verifyStatus(404)
            .verifyBody("404");
        server.invoke("GET", "/test2?code=505", new byte[0])
            .verifyStatus(505)
            .verifyBody("505");
    }

    @Test
    void testControllerWithCustomReaders() {
        var server = TestHttpServer.fromController(TestControllerWithCustomReaders.class);
        when(server.controller.test(any(), any(), any())).thenCallRealMethod();

        server.invoke("GET", "/test/fourth?queryEntity=first&queryEntity=second&queryEntity=third", new byte[0])
            .verifyStatus(200)
            .verifyBody("first, second, third, fourth");
    }

    @Test
    void testControllerWithInterceptors() {
        var server = TestHttpServer.fromController(TestControllerWithInterceptors.class);
        doCallRealMethod().when(server.controller).withMethodLevelInterceptors();
        doCallRealMethod().when(server.controller).withoutMethodLevelInterceptors();

        server.invoke("GET", "/withMethodLevelInterceptors", new byte[0])
            .verifyStatus(200);
    }
}


