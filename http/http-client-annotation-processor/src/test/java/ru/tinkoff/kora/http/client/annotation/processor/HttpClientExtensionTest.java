package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.client.common.response.*;
            import ru.tinkoff.kora.http.common.*;
            
            """;
    }

    @Test
    public void testHttpClientExtension() throws Exception {
        compile(List.of(new KoraAppProcessor(), new HttpClientAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              default ru.tinkoff.kora.http.client.common.HttpClient client() { return org.mockito.Mockito.mock(ru.tinkoff.kora.http.client.common.HttpClient.class) ;}
              default ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory telemetry() { return org.mockito.Mockito.mock(ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory.class) ;}
              default ru.tinkoff.kora.config.common.Config config() { return org.mockito.Mockito.mock(ru.tinkoff.kora.config.common.Config.class) ;}
              default ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<$TestClient_Config> extractor() { return org.mockito.Mockito.mock(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor.class) ;}

              @Root
              default String root(TestClient extractor) { return ""; }
            }
            """, """
            @ru.tinkoff.kora.http.client.common.annotation.HttpClient
            public interface TestClient {
              @ru.tinkoff.kora.http.common.annotation.HttpRoute(method = "POST", path = "/")
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
