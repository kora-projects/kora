package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;

public class ReactiveHttpControllerTest extends AbstractHttpControllerTest {
    @Test
    public void testReturnMonoResponse() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<HttpServerResponse> test() throws Exception {
                    return Mono.just(HttpServerResponse.of(200));
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnMonoResponseWithQueryParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<HttpServerResponse> test(@Query String queryParameter) {
                    return Mono.just(HttpServerResponse.of(200));
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
    public void testReturnMonoResponseWithRequestParameter() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<HttpServerResponse> test(String bodyParameter) throws Exception {
                    return Mono.just(HttpServerResponse.of(200, HttpBody.plaintext(bodyParameter)));
                }
            }
            """);

        var handler = module.getHandler("get_test", asyncStringRequestMapper());

        assertThat(handler, "POST", "/test", "test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnMonoVoid() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<Void> test() {
                    return Mono.empty();
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody(new byte[0]);
    }

    @Test
    public void testReturnMonoObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<String> test() {
                    return Mono.just("test");
                }
            }
            """);

        var handler = module.getHandler("get_test", strResponseMapper());

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("test");
    }

    @Test
    public void testReturnMonoResponseEntityObject() throws Exception {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                Mono<HttpResponseEntity<String>> test() {
                    return Mono.just(HttpResponseEntity.of(403, HttpHeaders.of("test-header", "test-value"), "test"));
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
    public void testMonoWithInterceptor() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                Mono<HttpServerResponse> test() {
                    return Mono.just(HttpServerResponse.of(200));
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
    public void testMonoWithInterceptorWithParameters() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor1.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @InterceptWith(TestInterceptor2.class)
                Mono<HttpServerResponse> test(@Query String queryParameter) {
                    return Mono.just(HttpServerResponse.of(200));
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
