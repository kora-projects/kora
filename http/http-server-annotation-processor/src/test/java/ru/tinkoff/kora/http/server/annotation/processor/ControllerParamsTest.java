package ru.tinkoff.kora.http.server.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.util.concurrent.CompletionStage;

public class ControllerParamsTest extends AbstractHttpControllerTest {
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

    private void verifyNoDependencies(Class<?> controllerModule) {
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).withFailMessage(moduleMethod + " has dependencies").hasSize(2);
        }
    }
}
