package ru.tinkoff.kora.aop.annotation.processor.aoptarget;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;
import ru.tinkoff.kora.common.AopAnnotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.Optional;
import java.util.Set;

public class AopTarget4 {
    public AopTarget4() {}

    @AopAnnotation
    public @interface TestAnnotation4 {
    }

    @TestAnnotation4()
    public String testMethod1(@TestAnnotation4() String arg) {
        return "test";
    }

    public static class Aspect4Factory implements KoraAspectFactory {

        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new Aspect4(processingEnvironment));
        }
    }

    public static class Aspect4 implements KoraAspect {
        private final ProcessingEnvironment env;

        public Aspect4(ProcessingEnvironment processingEnvironment) {
            this.env = processingEnvironment;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation4.class.getCanonicalName());
        }

        @Override
        public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
            return new ApplyResult.MethodBody(CodeBlock.of("return $L(arg);\n", superCall));
        }
    }
}
