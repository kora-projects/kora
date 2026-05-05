package io.koraframework.annotation.processor.common;

import com.palantir.javapoet.ClassName;
import io.koraframework.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.ExecutableElement;
import java.util.Set;

public final class TestAspectKoraAspect implements KoraAspect {

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ClassName.get(TestAspect.class));
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        return ApplyResult.Noop.INSTANCE;
    }
}
