package ru.tinkoff.kora.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

public class ExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.server.common.handler.*;
            import ru.tinkoff.kora.http.server.common.*;
            import ru.tinkoff.kora.http.common.*;
            """;
    }

    @Test
    public void testExtensionWithTag() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface Controller {
                @Root
                default String root(@Tag(String.class) HttpServerResponseMapper<HttpResponseEntity<String>> mapper) { return ""; }
            
                @Tag(String.class)
                default HttpServerResponseMapper<String> mapper() { return (rq, result) -> HttpServerResponse.of(200); }
            }
            """);

        compileResult.assertSuccess();
    }

    @Test
    public void testExtensionWithoutTag() {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface Controller {
                @Root
                default String root(HttpServerResponseMapper<HttpResponseEntity<String>> mapper) { return ""; }
            
                default HttpServerResponseMapper<String> mapper() { return (rq, result) -> HttpServerResponse.of(200); }
            }
            """);

        compileResult.assertSuccess();
    }
}
