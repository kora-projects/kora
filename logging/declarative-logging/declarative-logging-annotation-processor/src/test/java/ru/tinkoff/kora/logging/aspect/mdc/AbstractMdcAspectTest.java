package ru.tinkoff.kora.logging.aspect.mdc;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.util.List;

public abstract class AbstractMdcAspectTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.logging.common.annotation.Mdc;
            import ru.tinkoff.kora.logging.common.MDC;
            import ru.tinkoff.kora.logging.aspect.mdc.MDCContextHolder;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            import reactor.core.publisher.Mono;
            import reactor.core.publisher.Flux;
            """;
    }

    protected static List<String> sources(@Language("java") String... sources) {
        return List.of(sources);
    }
}
