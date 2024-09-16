package ru.tinkoff.kora.annotation.processor.common;

import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.ExecutableElement;
import java.util.Set;

public final class TestAspectKoraAspect implements KoraAspect {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(TestAspect.class.getCanonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        return ApplyResult.Noop.INSTANCE;
    }
}
