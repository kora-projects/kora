package io.koraframework.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.common.Either;
            import io.koraframework.http.client.common.response.*;
            import io.koraframework.http.common.*;
            import io.koraframework.json.common.JsonReader;
            import io.koraframework.json.common.annotation.Json;
            
            """;
    }

    @Test
    public void testHttpClientExtension() throws Exception {
        compile(List.of(new KoraAppProcessor(), new HttpClientAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              default io.koraframework.http.client.common.HttpClient client() { return org.mockito.Mockito.mock(io.koraframework.http.client.common.HttpClient.class) ;}
              default io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory telemetry() { return org.mockito.Mockito.mock(io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory.class) ;}
              default io.koraframework.config.common.Config config() { return org.mockito.Mockito.mock(io.koraframework.config.common.Config.class) ;}
              default io.koraframework.config.common.mapper.ConfigValueMapper<$TestClient_Module.Config> extractor() { return org.mockito.Mockito.mock(io.koraframework.config.common.mapper.ConfigValueMapper.class) ;}

              @Root
              default String root(TestClient extractor) { return ""; }
            }
            """, """
            @io.koraframework.http.client.common.annotation.HttpClient
            public interface TestClient {
              @io.koraframework.http.common.annotation.HttpRoute(method = "POST", path = "/")
              void test();
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraphDraw("TestApp");
        assertThat(graph.getNodes()).hasSize(7);
    }


    @Test
    public void testHttpClientExtensionWithAopProxy() throws Exception {
        compile(List.of(new KoraAppProcessor(), new HttpClientAnnotationProcessor(), new io.koraframework.aop.annotation.processor.AopAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              default io.koraframework.http.client.common.HttpClient client() { return org.mockito.Mockito.mock(io.koraframework.http.client.common.HttpClient.class) ;}
              default io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory telemetry() { return org.mockito.Mockito.mock(io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory.class) ;}
              default io.koraframework.config.common.Config config() { return org.mockito.Mockito.mock(io.koraframework.config.common.Config.class) ;}
              default io.koraframework.config.common.mapper.ConfigValueMapper<$TestClient_Module.Config> extractor() { return org.mockito.Mockito.mock(io.koraframework.config.common.mapper.ConfigValueMapper.class) ;}
              default org.slf4j.ILoggerFactory loggerFactory() { return org.slf4j.LoggerFactory.getILoggerFactory(); }

              @Root
              default String root(TestClient client) { return client.getClass().getName(); }
            }
            """, """
            @io.koraframework.http.client.common.annotation.HttpClient
            public interface TestClient {
              @io.koraframework.logging.common.annotation.Log
              @io.koraframework.http.common.annotation.HttpRoute(method = "POST", path = "/")
              void test();
            }
            """);
        compileResult.assertSuccess();

        // client is a nested class of its module and aop proxy is generated for the nested class
        var proxy = compileResult.loadClass("$TestClient_Module_Impl__AopProxy");
        assertThat(proxy.getSuperclass()).isEqualTo(compileResult.loadClass("$TestClient_Module$Impl"));
        var graph = loadGraphDraw("TestApp");
        // logger factory is a dependency of the proxy only, so it is in the graph only when the proxy is used
        assertThat(graph.getNodes()).hasSize(8);
    }

    @Test
    public void testExtensionWithTag() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(@Tag(String.class) HttpClientResponseMapper<HttpResponseEntity<String>> mapper) { return ""; }
            
                @Tag(String.class)
                default HttpClientResponseMapper<String> mapper() { return (rs) -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionWithoutTag() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(HttpClientResponseMapper<HttpResponseEntity<String>> mapper) { return ""; }
            
                default HttpClientResponseMapper<String> mapper() { return (rs) -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    /**
     * Untagged {@code HttpClientResponseMapper<HttpResponseEntity<T>>} must resolve to the plain
     * response entity mapper even when a {@code JsonReader<T>} is in the graph: the JSON flavour is
     * reachable only under the {@code @Json} tag. Without that tag on the JSON factory both default
     * template factories match and the graph fails with "Multiple components match dependency".
     */
    @Test
    public void testExtensionResponseEntityWhenJsonReaderIsPresent() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App extends io.koraframework.http.client.common.response.HttpClientResponseMapperModule {
                @Root
                default String root(HttpClientResponseMapper<HttpResponseEntity<String>> mapper) { return ""; }

                default JsonReader<String> reader() { return parser -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionJsonEither() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(@Json HttpClientResponseMapper<Either<String, String>> mapper) { return ""; }

                default JsonReader<String> reader() { return parser -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionJsonEitherTypeArguments() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(HttpClientResponseMapper<Either<@Json String, @Json String>> mapper) { return ""; }

                default JsonReader<String> reader() { return parser -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionJsonEitherResponseEntity() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(@Json HttpClientResponseMapper<HttpResponseEntity<Either<String, String>>> mapper) { return ""; }

                default JsonReader<String> reader() { return parser -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionJsonEitherResponseEntityTypeArguments() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App {
                @Root
                default String root(HttpClientResponseMapper<HttpResponseEntity<Either<@Json String, @Json String>>> mapper) { return ""; }

                default JsonReader<String> reader() { return parser -> ""; }
            }
            """);

        compileResult.assertSuccess();
    }

}
