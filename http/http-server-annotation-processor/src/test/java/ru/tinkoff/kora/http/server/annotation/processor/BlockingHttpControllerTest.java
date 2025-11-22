package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;

public class BlockingHttpControllerTest extends AbstractHttpControllerTest {
    @Test
    public void testReturnBlockingResponse() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnBlockingResponseWithQueryParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test(@Query String queryParameter) {
                    return HttpServerResponse.of(200);
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(new byte[0]);
        assertThat(handler, "GET", "/test?queryParameter")
            .hasStatus(400)
            .hasBody("Query parameter 'queryParameter' is required");
    }

    @Test
    public void testReturnBlockingResponseWithRequestParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test(String bodyParameter) {
                    return HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter));
                }
            }
            """);

        var handler = module.getHandler("get_test", stringRequestMapper());

        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnBlockingVoid() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                void test() {
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnBlockingObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                String test() {
                    return "test";
                }
            }
            """);

        var handler = module.getHandler("get_test", strResponseMapper());

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnBlockingResponseEntityObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                HttpResponseEntity<String> test() {
                    return HttpResponseEntity.of(403, HttpHeaders.of("test-header", "test-value"), "test");
                }
            }
            """);

        var handler = module.getHandler("get_test", new HttpServerResponseEntityMapper<>(strResponseMapper()));

        assertThat(handler, "GET", "/test")
            .hasStatus(403)
            .hasBody("test")
            .hasHeader("test-header", "test-value");
    }

    @Test
    public void testWithInterceptor() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """, """
            public class TestInterceptor1 implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400);
                    return chain.process(request);
                }
            }
            """, """
            public class TestInterceptor2 implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400);
                    return chain.process(request);
                }
            }
            """);

        var handler = module.getHandler("get_test", newObject("TestInterceptor1"), newObject("TestInterceptor2"));

        assertThat(handler, "GET", "/test?test")
            .hasStatus(200)
            .hasBody(new byte[0]);
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(new byte[0]);
    }

    @Test
    public void testWithInterceptorWithParameters() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                HttpServerResponse test(@Query String queryParameter) {
                    return HttpServerResponse.of(200);
                }
            }
            """, """
            public class TestInterceptor1 implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400);
                    return chain.process(request);
                }
            }
            """, """
            public class TestInterceptor2 implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return HttpServerResponse.of(400);
                    return chain.process(request);
                }
            }
            """);

        var handler = module.getHandler("get_test", newObject("TestInterceptor1"), newObject("TestInterceptor2"));

        assertThat(handler, "GET", "/test?queryParameter=test")
            .hasStatus(200)
            .hasBody(new byte[0]);
        assertThat(handler, "POST", "/test")
            .hasStatus(400)
            .hasBody(new byte[0]);
    }
}
