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
            import io.koraframework.http.client.common.response.*;
            import io.koraframework.http.common.*;
            
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
              default io.koraframework.config.common.extractor.ConfigValueExtractor<$TestClient_Config> extractor() { return org.mockito.Mockito.mock(io.koraframework.config.common.extractor.ConfigValueExtractor.class) ;}

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

}
