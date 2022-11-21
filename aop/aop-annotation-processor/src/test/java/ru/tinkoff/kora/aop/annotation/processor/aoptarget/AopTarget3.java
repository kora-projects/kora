package ru.tinkoff.kora.aop.annotation.processor.aoptarget;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;
import ru.tinkoff.kora.common.AopAnnotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.Optional;
import java.util.Set;

public class AopTarget3 {
    public AopTarget3() {}

    @AopAnnotation
    public @interface TestAnnotation3 {
    }

    @TestAnnotation3()
    public String testMethod1() {
        return "test";
    }

    @TestAnnotation3()
    public String testMethod2() {
        return "test";
    }

    public static class Aspect3Factory implements KoraAspectFactory {

        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new Aspect3(processingEnvironment));
        }
    }

    public static class Aspect3 implements KoraAspect {
        private final ProcessingEnvironment env;

        public Aspect3(ProcessingEnvironment processingEnvironment) {
            this.env = processingEnvironment;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation3.class.getCanonicalName());
        }

        @Override
        public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
            var field = aspectContext.fieldFactory().constructorInitialized(
                this.env.getElementUtils().getTypeElement("java.lang.String").asType(),
                CodeBlock.of("$S", executableElement.getSimpleName())
            );
            var b = CodeBlock.builder()
                .add("return $S + \"/\" + this.$L;", field, field);

            return new ApplyResult.MethodBody(b.build());
        }
    }
}
