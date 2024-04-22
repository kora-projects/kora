package ru.tinkoff.kora.http.server.annotation.processor;

import jakarta.annotation.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader;

import java.lang.reflect.ParameterizedType;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;

public class ControllerParamsTest extends AbstractHttpControllerTest {
    private final BlockingRequestExecutor executor = new BlockingRequestExecutor.Default(ForkJoinPool.commonPool());

    @Test
    public void testHeader() {
        compile("""
            @HttpController
            public class Controller {
                /*
                Headers: String, Integer, List<String>, List<Integer>
                 */

                @HttpRoute(method = GET, path = "/headerString")
                public void headerString(@Header(value = "string-header") String string) {
                }

                @HttpRoute(method = GET, path = "/headerNullableString")
                void headerNullableString(@Header @Nullable String string) {
                }

                @HttpRoute(method = GET, path = "/headerOptionalString")
                void headerNullableString(@Header Optional<String> string) {
                }

                @HttpRoute(method = GET, path = "/headerStringList")
                void headerNullableString(@Header List<String> string) {
                }

                @HttpRoute(method = GET, path = "/headerInteger")
                public void headerInteger(@Header(value = "integer-header") Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerNullableInteger")
                public void headerNullableInteger(@Header(value = "integer-header") @Nullable Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerOptionalInteger")
                public void headerOptionalInteger(@Header(value = "integer-header") Optional<Integer> integer) {
                }

                @HttpRoute(method = GET, path = "/headerIntegerList")
                public void headerStringList(@Header(value = "integer-header") List<Integer> integers) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testQuery() {
        compile("""
            @HttpController
            public class Controller {
                /*
                Query: String, Integer, Long, Double, Boolean, Enum<?>, List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>
                 */
                public enum TestEnum {
                    VAL1, VAL2
                }

                @HttpRoute(method = GET, path = "/queryString")
                public void queryString(@Query("value") String value1) {
                }

                @HttpRoute(method = GET, path = "/queryNullableString")
                public void queryNullableString(@Query @Nullable String value) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalString")
                public void queryOptionalString(@Query Optional<String> value) {
                }

                @HttpRoute(method = GET, path = "/queryStringList")
                public void queryStringList(@Query List<String> value) {
                }

                @HttpRoute(method = GET, path = "/queryInteger")
                public void queryInteger(@Query int value) {
                }

                @HttpRoute(method = GET, path = "/queryIntegerObject")
                public void queryIntegerObject(@Query Integer value) {
                }

                @HttpRoute(method = GET, path = "/queryNullableInteger")
                public void queryNullableInteger(@Query Integer value) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalInteger")
                public void queryOptionalInteger(@Query Optional<Integer> value) {
                }

                @HttpRoute(method = GET, path = "/queryIntegerList")
                public void queryIntegerList(@Query List<Integer> value) {
                }

                @HttpRoute(method = GET, path = "/queryLong")
                public void queryLong(@Query long value) {
                }

                @HttpRoute(method = GET, path = "/queryLongObject")
                public void queryLongObject(@Query Long value) {
                }

                @HttpRoute(method = GET, path = "/queryNullableLong")
                public void queryNullableLong(@Query Long value) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalLong")
                public void queryOptionalLong(@Query Optional<Long> value) {
                }

                @HttpRoute(method = GET, path = "/queryLongList")
                public void queryLongList(@Query List<Long> value) {
                }


                @HttpRoute(method = GET, path = "/queryDouble")
                public void queryDouble(@Query double value) {
                }

                @HttpRoute(method = GET, path = "/queryDoubleObject")
                public void queryDoubleObject(@Query Double value) {
                }

                @HttpRoute(method = GET, path = "/queryNullableDouble")
                public void queryNullableDouble(@Query Double value) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalDouble")
                public void queryOptionalDouble(@Query Optional<Double> value) {
                }

                @HttpRoute(method = GET, path = "/queryDoubleList")
                public void queryDoubleList(@Query List<Double> value) {
                }


                @HttpRoute(method = GET, path = "/queryBoolean")
                public void queryBoolean(@Query boolean value) {
                }

                @HttpRoute(method = GET, path = "/queryBooleanObject")
                public void queryBooleanObject(@Query Boolean value) {
                }

                @HttpRoute(method = GET, path = "/queryNullableBoolean")
                public void queryNullableBoolean(@Query Boolean value) {
                }

                @HttpRoute(method = GET, path = "/queryOptionalBoolean")
                public void queryOptionalBoolean(@Query Optional<Boolean> value) {
                }

                @HttpRoute(method = GET, path = "/queryBooleanList")
                public void queryBooleanList(@Query List<Boolean> value) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testQueryEnum() {
        compile("""
            @HttpController
            public class Controller {
            
                public enum TestEnum {
                    VAL1, VAL2
                }

                @HttpRoute(method = GET, path = "/queryEnum")
                public void queryString(@Query("value") TestEnum value1) { }

                @HttpRoute(method = GET, path = "/queryNullableEnum")
                public void queryNullableString(@Query @Nullable TestEnum value) { }

                @HttpRoute(method = GET, path = "/queryOptionalEnum")
                public void queryOptionalString(@Query Optional<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/queryListEnum")
                public void queryStringList(@Query List<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/queryNullableListEnum")
                public void queryNullableStringList(@Query @Nullable List<TestEnum> value) { }
            }
            """);

        compileResult.assertSuccess();
        final Class<?> controllerModule = compileResult.loadClass("ControllerModule");
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).hasSize(3);
            Assertions.assertThat(moduleMethod.getParameters()[2].getType()).isAssignableFrom(StringParameterReader.class);

            var type = ((ParameterizedType) moduleMethod.getParameters()[2].getParameterizedType());
            Assertions.assertThat(type.getActualTypeArguments()[0].getTypeName()).endsWith("TestEnum");
        }
    }

    @Test
    public void testHeaderEnum() {
        compile("""
            @HttpController
            public class Controller {
            
                public enum TestEnum {
                    VAL1, VAL2
                }

                @HttpRoute(method = GET, path = "/headerEnum")
                public void queryString(@Header TestEnum value1) { }

                @HttpRoute(method = GET, path = "/headerNullableEnum")
                public void queryNullableString(@Header @Nullable TestEnum value) { }

                @HttpRoute(method = GET, path = "/headerOptionalEnum")
                public void queryOptionalString(@Header Optional<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/headerListEnum")
                public void queryStringList(@Header List<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/headerNullableListEnum")
                public void queryNullableStringList(@Header @Nullable List<TestEnum> value) { }
            }
            """);

        compileResult.assertSuccess();
        final Class<?> controllerModule = compileResult.loadClass("ControllerModule");
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).hasSize(3);
            Assertions.assertThat(moduleMethod.getParameters()[2].getType()).isAssignableFrom(StringParameterReader.class);

            var type = ((ParameterizedType) moduleMethod.getParameters()[2].getParameterizedType());
            Assertions.assertThat(type.getActualTypeArguments()[0].getTypeName()).endsWith("TestEnum");
        }
    }

    @Test
    public void testHeaders() {
        compile("""
            @HttpController
            public class Controller {
                /*
                Headers: String, Integer, List<String>, List<Integer>
                 */

                @HttpRoute(method = GET, path = "/headerString")
                public void headerString(@Header(value = "string-header") String string) {
                }

                @HttpRoute(method = GET, path = "/headerNullableString")
                void headerNullableString(@Header @Nullable String string) {
                }

                @HttpRoute(method = GET, path = "/headerOptionalString")
                void headerNullableString(@Header Optional<String> string) {
                }

                @HttpRoute(method = GET, path = "/headerStringList")
                void headerNullableString(@Header List<String> string) {
                }

                @HttpRoute(method = GET, path = "/headerInteger")
                public void headerInteger(@Header(value = "integer-header") Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerNullableInteger")
                public void headerNullableInteger(@Header(value = "integer-header") @Nullable Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerOptionalInteger")
                public void headerOptionalInteger(@Header(value = "integer-header") Optional<Integer> integer) {
                }

                @HttpRoute(method = GET, path = "/headerIntegerList")
                public void headerStringList(@Header(value = "integer-header") List<Integer> integers) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testCookies() {
        compile("""
            import jakarta.annotation.Nullable;@HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/cookieString")
                public void cookieString(@Cookie(value = "someCookie") String string) {}

                @HttpRoute(method = GET, path = "/cookieNullableString")
                void cookieNullableString(@Cookie @Nullable String string) {}

                @HttpRoute(method = GET, path = "/cookieOptionalString")
                void cookieOptionalString(@Cookie Optional<String> string) {}

                @HttpRoute(method = GET, path = "/cookieCookie")
                void cookieCookie(@Cookie ru.tinkoff.kora.http.common.cookie.Cookie string) {}

                @HttpRoute(method = GET, path = "/cookieNullableCookie")
                void cookieNullableCookie(@Cookie @Nullable ru.tinkoff.kora.http.common.cookie.Cookie string) {}

                @HttpRoute(method = GET, path = "/cookieOptionalCookie")
                void cookieNullableCookie(@Cookie Optional<ru.tinkoff.kora.http.common.cookie.Cookie> string) {}
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testContext() {
        var m = compile("""
            import ru.tinkoff.kora.common.Context;

            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/ctx")
                public void context(Context ctx) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testRequest() {
        var m = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/request")
                public void request(HttpServerRequest request) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    public void testMappedRequestAsync() {
        var m = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/request")
                public CompletionStage<Void> request(String request) {
                  return CompletableFuture.completedFuture(null);
                }
            }
            """);
        compileResult.assertSuccess();
        var componentMethod = compileResult.loadClass("ControllerModule").getMethods()[0];
        Assertions.assertThat(componentMethod.getParameters()).hasSize(2);
        Assertions.assertThat(componentMethod.getGenericParameterTypes()[1]).isEqualTo(
            TypeRef.of(HttpServerRequestMapper.class, TypeRef.of(
                CompletionStage.class, String.class
            ))
        );
    }

    @Test
    public void testMappedRequest() {
        var m = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/request")
                public void request(String request) {
                }
            }
            """);
        compileResult.assertSuccess();
        var componentMethod = compileResult.loadClass("ControllerModule").getMethods()[0];
        Assertions.assertThat(componentMethod.getParameters()).hasSize(3);
        Assertions.assertThat(componentMethod.getGenericParameterTypes()[1]).isEqualTo(
            TypeRef.of(HttpServerRequestMapper.class, String.class)
        );
    }

    @Test
    public void testMappedRequestWithMappingAsync() {
        var m = compile("""
                @HttpController
                public class Controller {
                    @HttpRoute(method = GET, path = "/request")
                    public CompletionStage<Void> request(@Mapping(Mapper.class) String request) {
                      return CompletableFuture.completedFuture(null);
                    }
                }
                """,
            """
                public class Mapper implements HttpServerRequestMapper<CompletionStage<String>> {

                    @Override
                    public CompletionStage<String> apply(HttpServerRequest request) {
                        return CompletableFuture.completedFuture(request.toString());
                    }
                }
                """);
        compileResult.assertSuccess();
        var componentMethod = compileResult.loadClass("ControllerModule").getMethods()[0];
        Assertions.assertThat(componentMethod.getParameters()).hasSize(2);
        Assertions.assertThat(componentMethod.getGenericParameterTypes()[1]).isEqualTo(compileResult.loadClass("Mapper"));
    }

    @Test
    public void testMappedRequestWithMapping() {
        var m = compile("""
                @HttpController
                public class Controller {
                    @HttpRoute(method = GET, path = "/request")
                    public void request(@Mapping(Mapper.class) String request) {
                    }
                }
                """,
            """
                public class Mapper implements HttpServerRequestMapper<String> {

                    @Override
                    public String apply(HttpServerRequest request) {
                      return request.toString();
                    }
                }
                """);
        compileResult.assertSuccess();
        var componentMethod = compileResult.loadClass("ControllerModule").getMethods()[0];
        Assertions.assertThat(componentMethod.getParameters()).hasSize(3);
        Assertions.assertThat(componentMethod.getGenericParameterTypes()[1]).isEqualTo(compileResult.loadClass("Mapper"));
    }

    @Test
    public void testParseHeaderException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/test")
                public void test(@Header(value = "some-header") Object string) {
                }
            }
            """);
        compileResult.assertSuccess();
        var parser = new StringParameterReader<Object>() {
            @Override
            public Object read(String string) {
                System.out.println(string);
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("get_test", executor, parser);

        assertThat(handler, request("GET", "/test", "", HttpHeaders.of("some-header", "test")))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    public void testParseQueryException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/test")
                public void test(@Query(value = "q") Object string) {
                }
            }
            """);
        compileResult.assertSuccess();
        var parser = new StringParameterReader<Object>() {
            @Override
            public Object read(String string) {
                System.out.println(string);
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("get_test", executor, parser);

        assertThat(handler, request("GET", "/test?q=test", ""))
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    public void testParsePathException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/{string}/test")
                public void test(@Path Object string) {
                }
            }
            """);
        compileResult.assertSuccess();
        var parser = new StringParameterReader<Object>() {
            @Override
            public Object read(String string) {
                System.out.println(string);
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("get_string_test", executor, parser);

        var rq = request("GET", "/test/test", "");
        rq.pathParams().put("string", "test");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    public void testParseBodyMonoException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "POST", path = "/test")
                public Mono<Void> test(Object string) {
                  return null;
                }
            }
            """);
        compileResult.assertSuccess();

        var parser = new HttpServerRequestMapper<Object>() {
            @Nullable
            @Override
            public Object apply(HttpServerRequest request) throws Exception {
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("post_test", parser);

        var rq = request("GET", "/test/test", "");
        rq.pathParams().put("string", "test");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    public void testParseBodyFutureException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "POST", path = "/test")
                public CompletionStage<Void> test(Object string) {
                  return null;
                }
            }
            """);
        compileResult.assertSuccess();

        var parser = new HttpServerRequestMapper<Object>() {
            @Nullable
            @Override
            public Object apply(HttpServerRequest request) throws Exception {
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("post_test", parser);

        var rq = request("GET", "/test/test", "");
        rq.pathParams().put("string", "test");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }

    @Test
    public void testParseBodyException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "POST", path = "/test")
                public void test(Object string) {
                }
            }
            """);
        compileResult.assertSuccess();

        var parser = new HttpServerRequestMapper<Object>() {
            @Nullable
            @Override
            public Object apply(HttpServerRequest request) throws Exception {
                throw new RuntimeException("test-error");
            }
        };

        var handler = module.getHandler("post_test", parser, executor);

        var rq = request("GET", "/test/test", "");
        rq.pathParams().put("string", "test");
        assertThat(handler, rq)
            .hasStatus(400)
            .hasBody("test-error");
    }


    private void verifyNoDependencies(Class<?> controllerModule) {
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).withFailMessage(moduleMethod + " has dependencies").hasSize(2);
        }
    }
}
