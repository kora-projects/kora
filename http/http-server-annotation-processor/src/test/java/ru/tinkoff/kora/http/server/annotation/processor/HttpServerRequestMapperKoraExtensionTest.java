package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServerRequestMapperKoraExtensionTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testExtensionAnnotatedRecord() throws Exception {
        compile(List.of(new KoraAppProcessor()), """
            import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
            import java.util.concurrent.CompletionStage;
                        
            @KoraApp
            public interface TestApp {
              default HttpServerRequestMapper<CompletionStage<String>> stringMapper() {
                return rs -> rs.body().collectArray().thenApply(b -> new String(b));
              }
                        
              @Root
              default String root(HttpServerRequestMapper<String> extractor) { return ""; }
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(3);
    }
}
