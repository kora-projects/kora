package io.koraframework.aop.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.common.AopAnnotation;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

@AopAnnotation
public @interface TestAnnotation1 {
    String value();

    class TestAnnotationAspect extends AbstractTestAnnotationAspect {
        @Override
        protected ClassName testAnnotation() {
            return ClassName.get(TestAnnotation1.class);
        }
    }

    class TestAnnotationAspectFactory implements KoraAspectFactory {
        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new TestAnnotationAspect());
        }
    }
}
