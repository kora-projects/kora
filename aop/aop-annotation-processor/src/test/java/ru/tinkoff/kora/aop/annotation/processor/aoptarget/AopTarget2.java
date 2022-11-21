package ru.tinkoff.kora.aop.annotation.processor.aoptarget;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspectFactory;
import ru.tinkoff.kora.common.AopAnnotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.squareup.javapoet.CodeBlock.joining;

@AopTarget2.TestAnnotation21("TestAnnotation21")
public class AopTarget2 {
    public interface ProxyListener2 {
        void call(String annotationValue);
    }

    @AopAnnotation
    public @interface TestAnnotation21 {
        String value();
    }

    @AopAnnotation
    public @interface TestAnnotation22 {
        String value();
    }

    @TestAnnotation22("TestAnnotation22")
    public void testMethod1() {}

    @TestAnnotation21("TestAnnotation21Method")
    @TestAnnotation22("TestAnnotation22")
    public void testMethod2() {}

    @TestAnnotation22("TestAnnotation22")
    @TestAnnotation21("TestAnnotation21Method")
    public void testMethod3() {}

    public void testMethod4(@TestAnnotation22("TestAnnotation22Param") String param) {}

    public static class Aspect21Factory implements KoraAspectFactory {

        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new Aspect21(processingEnvironment));
        }
    }

    public static class Aspect21 implements KoraAspect {
        private final ProcessingEnvironment env;

        public Aspect21(ProcessingEnvironment processingEnvironment) {
            this.env = processingEnvironment;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation21.class.getCanonicalName());
        }

        @Override
        public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
            var annotation = executableElement.getAnnotation(TestAnnotation21.class);
            if (annotation == null) {
                annotation = executableElement.getEnclosingElement().getAnnotation(TestAnnotation21.class);
            }
            var field = aspectContext.fieldFactory().constructorParam(
                this.env.getElementUtils().getTypeElement(ProxyListener2.class.getCanonicalName()).asType(),
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

    public static class Aspect22Factory implements KoraAspectFactory {

        @Override
        public Optional<KoraAspect> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new Aspect22(processingEnvironment));
        }
    }

    public static class Aspect22 implements KoraAspect {
        private final ProcessingEnvironment env;

        public Aspect22(ProcessingEnvironment processingEnvironment) {
            this.env = processingEnvironment;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation22.class.getCanonicalName());
        }

        @Override
        public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
            var annotation = executableElement.getAnnotation(TestAnnotation22.class);
            if (annotation == null) {
                for (var parameter : executableElement.getParameters()) {
                    annotation = parameter.getAnnotation(TestAnnotation22.class);
                    if (annotation != null) {
                        break;
                    }
                }
            }
            var field = aspectContext.fieldFactory().constructorParam(
                this.env.getElementUtils().getTypeElement(ProxyListener2.class.getCanonicalName()).asType(),
                List.of()
            );
            var b = CodeBlock.builder()
                .add("this.$L.call($S);\n", field, annotation.value());
            if (executableElement.getReturnType().getKind() != TypeKind.VOID) {
                b.add("return ");
            }
            b.add("$L(", superCall);
            for (int i = 0; i < executableElement.getParameters().size(); i++) {
                if (i > 0) {
                    b.add(", ");
                }
                var parameter = executableElement.getParameters().get(i);
                b.add("$L", parameter);
            }
            b.add(");\n");

            return new ApplyResult.MethodBody(b.build());
        }
    }
}
