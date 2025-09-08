package ru.tinkoff.kora.aop.annotation.processor;

import com.palantir.javapoet.ClassName;
import ru.tinkoff.kora.common.AopAnnotation;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Optional;

@AopAnnotation
public @interface TestAnnotation2 {
    String value();


    class TestAnnotationAspect extends AbstractTestAnnotationAspect {
        @Override
        protected ClassName testAnnotation() {
            return ClassName.get(TestAnnotation2.class);
        }
    }

    class TestAnnotationAspectFactory implements KoraAspectFactory {
        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new TestAnnotation2.TestAnnotationAspect());
        }
    }
}
