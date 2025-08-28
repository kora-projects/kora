package ru.tinkoff.kora.http.server.annotation.processor;

import jakarta.annotation.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
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
    void testPath() {
        compile("""
            @HttpController
            public class Controller {
            
                @HttpRoute(method = GET, path = "/pathBoolean/{value}")
                void pathBoolean(@Path Boolean value) { }

                @HttpRoute(method = GET, path = "/pathString/{someValue}")
                void pathString(@Path(value = "someValue") String string) { }
            
                @HttpRoute(method = GET, path = "/pathInteger/{value}")
                void pathInteger(@Path int value) { }
            
                @HttpRoute(method = GET, path = "/pathLong/{value}")
                void pathLong(@Path long value) { }
            
                @HttpRoute(method = GET, path = "/pathDouble/{value}")
                void pathDouble(@Path double value) { }
            
                @HttpRoute(method = GET, path = "/pathUUID/{value}")
                void pathUUID(@Path UUID value) { }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testHeader() {
        compile("""
            @HttpController
            public class Controller {
                /*
                Headers: String, Integer, List<String>, List<Integer>
                 */

                @HttpRoute(method = GET, path = "/headerString")
                void headerString(@Header(value = "string-header") String string) { }
            
                @HttpRoute(method = GET, path = "/headerStringNullable")
                void headerStringNullable(@Header @Nullable String string) { }
            
                @HttpRoute(method = GET, path = "/headerStringOptional")
                void headerStringOptional(@Header Optional<String> string) { }
            
                @HttpRoute(method = GET, path = "/headerStringList")
                void headerStringList(@Header List<String> values) { }
            
                @HttpRoute(method = GET, path = "/headerStringListNullable")
                void headerStringListNullable(@Header @Nullable List<String> values) { }
            
                @HttpRoute(method = GET, path = "/headerStringSet")
                void headerStringSet(@Header Set<String> values) { }
            
                @HttpRoute(method = GET, path = "/headerStringSetNullable")
                void headerStringSetNullable(@Header @Nullable Set<String> values) { }
            
                @HttpRoute(method = GET, path = "/headerInteger")
                void headerInteger(@Header(value = "integer-header") int integer) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerNullable")
                void headerIntegerNullable(@Header(value = "integer-header") @Nullable Integer integer) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerOptional")
                void headerIntegerOptional(@Header(value = "integer-header") Optional<Integer> integer) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerList")
                void headerIntegerList(@Header(value = "integer-header") List<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerListNullable")
                void headerIntegerListNullable(@Header(value = "integer-header") @Nullable List<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSet")
                void headerIntegerSet(@Header(value = "integer-header") Set<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/headerIntegerSetNullable")
                void headerIntegerSetNullable(@Header(value = "integer-header") @Nullable Set<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/headerLong")
                void headerLong(@Header long value) { }
            
                @HttpRoute(method = GET, path = "/headerLongNullable")
                void headerLongNullable(@Header @Nullable Long value) { }
            
                @HttpRoute(method = GET, path = "/headerLongOptional")
                void headerLongOptional(@Header Optional<Long> value) { }
            
                @HttpRoute(method = GET, path = "/headerLongList")
                void headerLongList(@Header List<Long> values) { }
            
                @HttpRoute(method = GET, path = "/headerLongListNullable")
                void headerLongListNullable(@Header @Nullable List<Long> values) { }
            
                @HttpRoute(method = GET, path = "/headerLongSet")
                void headerLongSet(@Header Set<Long> values) { }
            
                @HttpRoute(method = GET, path = "/headerLongSetNullable")
                void headerLongSetNullable(@Header @Nullable Set<Long> values) { }
            
                @HttpRoute(method = GET, path = "/headerDouble")
                void headerDouble(@Header double value) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleNullable")
                void headerDoubleNullable(@Header @Nullable Double value) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleOptional")
                void headerDoubleOptional(@Header Optional<Double> value) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleList")
                void headerDoubleList(@Header List<Double> values) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleListNullable")
                void headerDoubleListNullable(@Header @Nullable List<Double> values) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSet")
                void headerDoubleSet(@Header Set<Double> values) { }
            
                @HttpRoute(method = GET, path = "/headerDoubleSetNullable")
                void headerDoubleSetNullable(@Header @Nullable Set<Double> values) { }
            
                @HttpRoute(method = GET, path = "/headerUUID")
                void headerUUID(@Header UUID value) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDNullable")
                void headerUUIDNullable(@Header @Nullable UUID value) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDOptional")
                void headerUUIDOptional(@Header Optional<UUID> value) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDList")
                void headerUUIDList(@Header List<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDListNullable")
                void headerUUIDListNullable(@Header @Nullable List<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSet")
                void headerUUIDSet(@Header Set<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/headerUUIDSetNullable")
                void headerUUIDSetNullable(@Header @Nullable Set<UUID> values) { }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testHeaderCustomStringReader() {
        compile("""
            @HttpController
            public class Controller {
            
                @HttpRoute(method = GET, path = "/headerBigInteger")
                void headerBigInteger(@Header BigInteger value) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerNullable")
                void headerBigIntegerNullable(@Header @Nullable BigInteger value) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerOptional")
                void headerBigIntegerOptional(@Header Optional<BigInteger> value) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerList")
                void headerBigIntegerList(@Header List<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerListNullable")
                void headerBigIntegerListNullable(@Header @Nullable List<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSet")
                void headerBigIntegerSet(@Header Set<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/headerBigIntegerSetNullable")
                void headerBigIntegerSetNullable(@Header @Nullable Set<BigInteger> values) { }
            }
            """);

        compileResult.assertSuccess();
        final Class<?> controllerModule = compileResult.loadClass("ControllerModule");
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).hasSize(3);
            Assertions.assertThat(moduleMethod.getParameters()[2].getType()).isAssignableFrom(StringParameterReader.class);

            var type = ((ParameterizedType) moduleMethod.getParameters()[2].getParameterizedType());
            Assertions.assertThat(type.getActualTypeArguments()[0].getTypeName()).endsWith("BigInteger");
        }
    }

    @Test
    void testQuery() {
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
                void queryString(@Query(value = "string-query") String string) { }
            
                @HttpRoute(method = GET, path = "/queryStringNullable")
                void queryStringNullable(@Query @Nullable String string) { }
            
                @HttpRoute(method = GET, path = "/queryStringOptional")
                void queryStringOptional(@Query Optional<String> string) { }
            
                @HttpRoute(method = GET, path = "/queryStringList")
                void queryStringList(@Query List<String> values) { }
            
                @HttpRoute(method = GET, path = "/queryStringListNullable")
                void queryStringListNullable(@Query @Nullable List<String> values) { }
            
                @HttpRoute(method = GET, path = "/queryStringSet")
                void queryStringSet(@Query Set<String> values) { }
            
                @HttpRoute(method = GET, path = "/queryStringSetNullable")
                void queryStringSetNullable(@Query @Nullable Set<String> values) { }
            
                @HttpRoute(method = GET, path = "/queryInteger")
                void queryInteger(@Query(value = "integer-query") int integer) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerNullable")
                void queryIntegerNullable(@Query(value = "integer-query") @Nullable Integer integer) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerOptional")
                void queryIntegerOptional(@Query(value = "integer-query") Optional<Integer> integer) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerList")
                void queryIntegerList(@Query(value = "integer-query") List<Integer> integers) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerListNullable")
                void queryIntegerListNullable(@Query(value = "integer-query") @Nullable List<Integer> integers) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSet")
                void queryIntegerSet(@Query Set<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/queryIntegerSetNullable")
                void queryIntegerSetNullable(@Query @Nullable Set<Integer> values) { }
            
                @HttpRoute(method = GET, path = "/queryLong")
                void queryLong(@Query("valueSome") long value) { }
            
                @HttpRoute(method = GET, path = "/queryLongNullable")
                void queryLongNullable(@Query @Nullable Long value) { }
            
                @HttpRoute(method = GET, path = "/queryLongOptional")
                void queryLongOptional(@Query Optional<Long> value) { }
            
                @HttpRoute(method = GET, path = "/queryLongList")
                void queryLongList(@Query List<Long> values) { }
            
                @HttpRoute(method = GET, path = "/queryLongListNullable")
                void queryLongListNullable(@Query @Nullable List<Long> values) { }
            
                @HttpRoute(method = GET, path = "/queryLongSet")
                void queryLongSet(@Query Set<Long> values) { }
            
                @HttpRoute(method = GET, path = "/queryLongSetNullable")
                void queryLongSetNullable(@Query @Nullable Set<Long> values) { }
            
                @HttpRoute(method = GET, path = "/queryDouble")
                void queryDouble(@Query("valueSome") double value) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleNullable")
                void queryDoubleNullable(@Query @Nullable Double value) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleOptional")
                void queryDoubleOptional(@Query Optional<Double> value) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleList")
                void queryDoubleList(@Query List<Double> values) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleListNullable")
                void queryDoubleListNullable(@Query @Nullable List<Double> values) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSet")
                void queryDoubleSet(@Query Set<Double> values) { }
            
                @HttpRoute(method = GET, path = "/queryDoubleSetNullable")
                void queryDoubleSetNullable(@Query @Nullable Set<Double> values) { }
            
                @HttpRoute(method = GET, path = "/queryUUID")
                void queryUUID(@Query("valueSome") UUID value) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDNullable")
                void queryUUIDNullable(@Query @Nullable UUID value) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDOptional")
                void queryUUIDOptional(@Query Optional<UUID> value) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDList")
                void queryUUIDList(@Query List<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDListNullable")
                void queryUUIDListNullable(@Query @Nullable List<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSet")
                void queryUUIDSet(@Query Set<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/queryUUIDSetNullable")
                void queryUUIDSetNullable(@Query @Nullable Set<UUID> values) { }
            
                @HttpRoute(method = GET, path = "/queryBoolean")
                void queryBoolean(@Query("valueSome") boolean value) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanNullable")
                void queryBooleanNullable(@Query @Nullable Boolean value) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanOptional")
                void queryBooleanOptional(@Query Optional<Boolean> value) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanList")
                void queryBooleanList(@Query List<Boolean> values) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanListNullable")
                void queryBooleanListNullable(@Query @Nullable List<Boolean> values) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSet")
                void queryBooleanSet(@Query Set<Boolean> values) { }
            
                @HttpRoute(method = GET, path = "/queryBooleanSetNullable")
                void queryBooleanSetNullable(@Query @Nullable Set<Boolean> values) { }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testQueryCustomStringReader() {
        compile("""
            @HttpController
            public class Controller {
            
                @HttpRoute(method = GET, path = "/queryBigInteger")
                void queryBigInteger(@Query BigInteger value) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerNullable")
                void queryBigIntegerNullable(@Query @Nullable BigInteger value) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerOptional")
                void queryBigIntegerOptional(@Query Optional<BigInteger> value) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerList")
                void queryBigIntegerList(@Query List<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerListNullable")
                void queryBigIntegerListNullable(@Query @Nullable List<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSet")
                void queryBigIntegerSet(@Query Set<BigInteger> values) { }
            
                @HttpRoute(method = GET, path = "/queryBigIntegerSetNullable")
                void queryBigIntegerSetNullable(@Query @Nullable Set<BigInteger> values) { }
            }
            """);

        compileResult.assertSuccess();
        final Class<?> controllerModule = compileResult.loadClass("ControllerModule");
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).hasSize(3);
            Assertions.assertThat(moduleMethod.getParameters()[2].getType()).isAssignableFrom(StringParameterReader.class);

            var type = ((ParameterizedType) moduleMethod.getParameters()[2].getParameterizedType());
            Assertions.assertThat(type.getActualTypeArguments()[0].getTypeName()).endsWith("BigInteger");
        }
    }

    @Test
    void testQueryEnum() {
        compile("""
            @HttpController
            public class Controller {
            
                public enum TestEnum {
                    VAL1, VAL2
                }

                @HttpRoute(method = GET, path = "/queryEnum")
                void queryEnum(@Query("valueSome") TestEnum value) { }
            
                @HttpRoute(method = GET, path = "/queryEnumNullable")
                void queryEnumNullable(@Query @Nullable TestEnum value) { }
            
                @HttpRoute(method = GET, path = "/queryEnumOptional")
                void queryEnumOptional(@Query Optional<TestEnum> value) { }
            
                @HttpRoute(method = GET, path = "/queryEnumList")
                void queryEnumList(@Query List<TestEnum> values) { }
            
                @HttpRoute(method = GET, path = "/queryEnumListNullable")
                void queryEnumListNullable(@Query @Nullable List<TestEnum> values) { }
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
    void testHeaderEnum() {
        compile("""
            @HttpController
            public class Controller {
            
                public enum TestEnum {
                    VAL1, VAL2
                }

                @HttpRoute(method = GET, path = "/headerEnum")
                void queryString(@Header TestEnum value1) { }

                @HttpRoute(method = GET, path = "/headerNullableEnum")
                void queryNullableString(@Header @Nullable TestEnum value) { }

                @HttpRoute(method = GET, path = "/headerOptionalEnum")
                void queryOptionalString(@Header Optional<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/headerListEnum")
                void queryStringList(@Header List<TestEnum> value) { }

                @HttpRoute(method = GET, path = "/headerNullableListEnum")
                void queryNullableStringList(@Header @Nullable List<TestEnum> value) { }
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
    void testHeaders() {
        compile("""
            @HttpController
            public class Controller {
                /*
                Headers: String, Integer, List<String>, List<Integer>
                 */

                @HttpRoute(method = GET, path = "/headerString")
                void headerString(@Header(value = "string-header") String string) {
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
                void headerInteger(@Header(value = "integer-header") Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerNullableInteger")
                void headerNullableInteger(@Header(value = "integer-header") @Nullable Integer integer) {
                }

                @HttpRoute(method = GET, path = "/headerOptionalInteger")
                void headerOptionalInteger(@Header(value = "integer-header") Optional<Integer> integer) {
                }

                @HttpRoute(method = GET, path = "/headerIntegerList")
                void headerStringList(@Header(value = "integer-header") List<Integer> integers) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testCookies() {
        compile("""
            import jakarta.annotation.Nullable;@HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/cookieString")
                void cookieString(@Cookie(value = "someCookie") String string) {}

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
    void testContext() {
        var m = compile("""
            import ru.tinkoff.kora.common.Context;

            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/ctx")
                void context(Context ctx) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testRequest() {
        var m = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/request")
                void request(HttpServerRequest request) {
                }
            }
            """);

        compileResult.assertSuccess();
        verifyNoDependencies(compileResult.loadClass("ControllerModule"));
    }

    @Test
    void testMappedRequestAsync() {
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
    void testMappedRequest() {
        var m = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/request")
                void request(String request) {
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
    void testMappedRequestWithMappingAsync() {
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
    void testMappedRequestWithMapping() {
        var m = compile("""
                @HttpController
                public class Controller {
                    @HttpRoute(method = GET, path = "/request")
                    void request(@Mapping(Mapper.class) String request) {
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
    void testParseHeaderException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/test")
                void test(@Header(value = "some-header") Object string) {
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
    void testParseQueryException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/test")
                void test(@Query(value = "q") Object string) {
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
    void testParsePathException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = GET, path = "/{string}/test")
                void test(@Path Object string) {
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
    void testParseBodyMonoException() {
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
    void testParseBodyFutureException() {
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
    void testParseBodyException() {
        var module = compile("""
            @HttpController
            public class Controller {
                @HttpRoute(method = "POST", path = "/test")
                void test(Object string) {
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

    @Test
    void testControllerTag() {
        compile("""
            @Tag(String.class)
            @HttpController
            public class Controller {
            
                @HttpRoute(method = GET, path = "/pathString/{someValue}")
                void pathString(@Path(value = "someValue") String string) { }
            }
            """);

        compileResult.assertSuccess();
        Class<?> module = compileResult.loadClass("ControllerModule");
        verifyNoDependencies(module);
        Class<?> controller = compileResult.loadClass("Controller");
        Assertions.assertThat(controller.getAnnotation(Tag.class)).isNotNull();
        Assertions.assertThat(module.getDeclaredMethods()[0].getAnnotation(Tag.class)).isNotNull();
        Assertions.assertThat(module.getDeclaredMethods()[0].getParameters()[0].getAnnotation(Tag.class)).isNotNull();
    }

    private void verifyNoDependencies(Class<?> controllerModule) {
        for (var moduleMethod : controllerModule.getMethods()) {
            Assertions.assertThat(moduleMethod.getParameters()).withFailMessage(moduleMethod + " has dependencies").hasSize(2);
        }
    }
}
