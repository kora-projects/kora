package ru.tinkoff.kora.aop.annotation.processor.aoptarget;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;
import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.squareup.javapoet.CodeBlock.joining;

public class AopTarget1 {
    public AopTarget1(String argument, @Tag(String.class) Integer tagged) {}

    public interface ProxyListener1 {
        void call(String annotationValue);
    }

    @AopAnnotation
    public @interface TestAnnotation1 {
        String value();
    }

    public void shouldNotBeProxied1() {}

    void shouldNotBeProxied2() {}

    @TestAnnotation1("testMethod1")
    public String testMethod1() {
        return "test";
    }

    @TestAnnotation1("testMethod2")
    protected void testMethod2(@Nullable String param) {}

    public static class Aspect1Factory implements KoraAspectFactory {

        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new Aspect1(processingEnvironment));
        }
    }

    public static class Aspect1 implements KoraAspect {
        private final ProcessingEnvironment env;

        public Aspect1(ProcessingEnvironment processingEnvironment) {
            this.env = processingEnvironment;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation1.class.getCanonicalName());
        }

        @Override
        public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
            var annotation = executableElement.getAnnotation(TestAnnotation1.class);
            var field = aspectContext.fieldFactory().constructorParam(
                this.env.getElementUtils().getTypeElement(ProxyListener1.class.getCanonicalName()).asType(),
                List.of()
            );
            var b = CodeBlock.builder()
                .add("this.$L.call($S);\n", field, annotation.value());
            if (executableElement.getReturnType().getKind() != TypeKind.VOID) {
                b.add("return ");
            }

            b.add(executableElement.getParameters().stream()
                .map(p -> CodeBlock.of("$L", p))
                .collect(joining(", ", superCall + "(", ");\n")));

            return new ApplyResult.MethodBody(b.build());
        }
    }
}
