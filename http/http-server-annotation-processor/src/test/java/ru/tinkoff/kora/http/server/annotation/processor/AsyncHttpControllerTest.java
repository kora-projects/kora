package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;

public class AsyncHttpControllerTest extends AbstractHttpControllerTest {
    @Test
    public void testReturnCompletionStageResponse() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<HttpServerResponse> test() {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnCompletionStageResponseWithQueryParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<HttpServerResponse> test(@Query String queryParameter) {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
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
    public void testReturnCompletionStageResponseWithRequestParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<HttpServerResponse> test(String bodyParameter) throws Exception {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter)));
                }
            }
            """);

        var handler = module.getHandler("get_test", asyncStringRequestMapper());

        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnCompletionStageVoid() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<Void> test() {
                    return CompletableFuture.completedFuture(null);
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnCompletionStageObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<String> test() {
                    return CompletableFuture.completedFuture("test");
                }
            }
            """);

        var handler = module.getHandler("get_test", strResponseMapper());

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnCompletionStageResponseEntityObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletionStage<HttpResponseEntity<String>> test() {
                    return CompletableFuture.completedFuture(HttpResponseEntity.of(403, HttpHeaders.of("test-header", "test-value"), "test"));
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
    public void testReturnCompletableFutureResponse() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<HttpServerResponse> test() {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnCompletableFutureResponseWithQueryParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<HttpServerResponse> test(@Query String queryParameter) {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
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
    public void testReturnCompletableFutureResponseWithRequestParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<HttpServerResponse> test(String bodyParameter) {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter)));
                }
            }
            """);

        var handler = module.getHandler("get_test", asyncStringRequestMapper());

        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnCompletableFutureVoid() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<Void> test() {
                    return CompletableFuture.completedFuture(null);
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnCompletableFutureObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<String> test() {
                    return CompletableFuture.completedFuture("test");
                }
            }
            """);

        var handler = module.getHandler("get_test", strResponseMapper());

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnCompletableFutureResponseEntityObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                CompletableFuture<HttpResponseEntity<String>> test() {
                    return CompletableFuture.completedFuture(HttpResponseEntity.of(403, HttpHeaders.of("test-header", "test-value"), "test"));
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
    public void testCompletableFutureWithInterceptor() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                CompletableFuture<HttpServerResponse> test() {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
                }
            }
            """, """
            public class TestInterceptor1 implements HttpServerInterceptor {
                @Override
                public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
                }
            }
            """, """
            public class TestInterceptor2 implements HttpServerInterceptor {
                @Override
                public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
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
    public void testCompletableFutureWithInterceptorWithParameters() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                CompletableFuture<HttpServerResponse> test(@Query String queryParameter) {
                    return CompletableFuture.completedFuture(HttpServerResponse.of(200));
                }
            }
            """, """
            public class TestInterceptor1 implements HttpServerInterceptor {
                @Override
                public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
                }
            }
            """, """
            public class TestInterceptor2 implements HttpServerInterceptor {
                @Override
                public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    if (request.queryParams().isEmpty()) return CompletableFuture.completedFuture(HttpServerResponse.of(400));
                    return chain.process(context, request);
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
