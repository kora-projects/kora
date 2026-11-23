package io.koraframework.http.server.annotation.processor;

import io.koraframework.http.server.common.response.mapper.HttpServerResponseEntityMapper;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testParametersReadRequestModifiedByInterceptor() {
        var module = this.compile("""
            @HttpController
            @InterceptWith(TestInterceptor.class)
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test(@Header("X-User") String user, @Query int limit, HttpServerRequest request) {
                    return HttpServerResponse.of(200, HttpBody.plaintext(user + ":" + limit + ":" + request.headers().getFirst("X-User")));
                }
            }
            """, """
            public class TestInterceptor implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) throws Exception {
                    return chain.process(request.toBuilder().header("X-User", "alice").queryParam("limit", 10).build());
                }
            }
            """);

        var handler = module.getHandler("get_test", newObject("TestInterceptor"));

        assertThat(handler, "GET", "/test")
            .hasStatus(200)
            .hasBody("alice:10:alice");
    }

    @Test
    public void testReturnNullableResponse() {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/test")
                @Nullable
                HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """);

        assertThat(module.getHandler("get_test"), "GET", "/test")
            .hasStatus(200);
    }

    @Test
    public void testDistinctRoutesGetDistinctHandlers() {
        var module = this.compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "GET", path = "/files")
                HttpServerResponse files() { return HttpServerResponse.of(200, HttpBody.plaintext("files")); }

                @HttpRoute(method = "GET", path = "/files/*")
                HttpServerResponse file() { return HttpServerResponse.of(200, HttpBody.plaintext("file")); }

                @HttpRoute(method = "GET", path = "/users/{id}")
                HttpServerResponse userById(@Path String id) { return HttpServerResponse.of(200); }

                @HttpRoute(method = "GET", path = "/users/id")
                HttpServerResponse userId() { return HttpServerResponse.of(200); }

                @HttpRoute(method = "GET", path = "/привет")
                HttpServerResponse hello() { return HttpServerResponse.of(200); }

                @HttpRoute(method = "GET", path = "/мир")
                HttpServerResponse world() { return HttpServerResponse.of(200); }

                @HttpRoute(method = "VERSION-CONTROL", path = "/doc")
                HttpServerResponse versionControl() { return HttpServerResponse.of(200); }
            }
            """);

        var moduleClass = compileResult.loadClass("ControllerModule");
        org.assertj.core.api.Assertions.assertThat(moduleClass.getMethods()).hasSize(7);
        assertThat(module.getHandler("get_files"), "GET", "/files").hasBody("files");
        assertThat(module.getHandler("get_files_file"), "GET", "/files/a").hasBody("file");
        org.assertj.core.api.Assertions.assertThat(module.getHandler("version_control_doc").method()).isEqualTo("VERSION-CONTROL");
    }

    @Test
    public void testRouteRedeclaredOnOverride() {
        var module = this.compile("""
            public interface Api {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test();
            }
            """, """
            @HttpController
            public class Controller implements Api {
                @Override
                @HttpRoute(method = "GET", path = "/test")
                public HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """);

        org.assertj.core.api.Assertions.assertThat(compileResult.loadClass("ControllerModule").getMethods()).hasSize(1);
        assertThat(module.getHandler("get_test"), "GET", "/test")
            .hasStatus(200);
    }

    @Test
    public void testInterceptorOnOverrideOfInheritedRoute() {
        var module = this.compile("""
            public interface Api {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test();
            }
            """, """
            @HttpController
            public class Controller implements Api {
                @Override
                @InterceptWith(TestInterceptor.class)
                public HttpServerResponse test() {
                    return HttpServerResponse.of(200);
                }
            }
            """, """
            public class TestInterceptor implements HttpServerInterceptor {
                @Override
                public HttpServerResponse intercept(HttpServerRequest request, HttpServerInterceptor.InterceptChain chain) {
                    return HttpServerResponse.of(403);
                }
            }
            """);

        var handler = module.getHandler("get_test", newObject("TestInterceptor"));

        assertThat(handler, "GET", "/test")
            .hasStatus(403);
    }

    @Test
    public void testNestedControllersWithSameSimpleName() {
        var result = compile(java.util.List.of(new HttpControllerProcessor()), """
            public class UserApi {
                @HttpController
                public static class Controller {
                    @HttpRoute(method = "GET", path = "/users")
                    public HttpServerResponse get() { return HttpServerResponse.of(200); }
                }
            }
            """, """
            public class OrderApi {
                @HttpController
                public static class Controller {
                    @HttpRoute(method = "GET", path = "/orders")
                    public HttpServerResponse get() { return HttpServerResponse.of(200); }
                }
            }
            """);

        result.assertSuccess();
        org.assertj.core.api.Assertions.assertThat(result.loadClass("UserApi_ControllerModule").getMethods()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(result.loadClass("OrderApi_ControllerModule").getMethods()).hasSize(1);
    }

    @Test
    public void testOverrideRepeatingRouteKeepsParentParameterAnnotations() {
        var module = this.compile("""
            public interface Api {
                @HttpRoute(method = "GET", path = "/test")
                HttpServerResponse test(@Header("X-Id") String id, @Query String q);
            }
            """, """
            @HttpController
            public class Controller implements Api {
                @Override
                @HttpRoute(method = "GET", path = "/test")
                public HttpServerResponse test(String id, String q) {
                    return HttpServerResponse.of(200, HttpBody.plaintext(id + "|" + q));
                }
            }
            """);

        var handler = module.getHandler("get_test");

        assertThat(handler, request("GET", "/test?q=x", "", io.koraframework.http.common.header.HttpHeaders.of("X-Id", "1")))
            .hasStatus(200)
            .hasBody("1|x");
    }

    @Test
    public void testRootControllerWithEmptyRoutePathMapsToSlash() {
        var module = this.compile("""
            @HttpController("/")
            public class Controller {
                @HttpRoute(method = "GET", path = "")
                public HttpServerResponse index() {
                    return HttpServerResponse.of(200);
                }
            }
            """);

        var handler = module.getHandler("get__trailing_slash");
        org.assertj.core.api.Assertions.assertThat(handler.routeTemplate()).isEqualTo("/");
    }
}
