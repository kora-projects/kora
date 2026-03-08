package io.koraframework.logging.aspect.mdc;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import org.intellij.lang.annotations.Language;

import java.util.List;

public abstract class AbstractMdcAspectTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.logging.common.annotation.Mdc;
            import io.koraframework.logging.common.MDC;
            import io.koraframework.logging.aspect.mdc.MDCContextHolder;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            """;
    }

    protected static List<String> sources(@Language("java") String... sources) {
        return List.of(sources);
    }
}
