package io.koraframework.http.server.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

public class ExtensionTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.http.server.common.request.*;
            import io.koraframework.http.server.common.response.*;
            import io.koraframework.http.server.common.*;
            import io.koraframework.http.common.*;
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
