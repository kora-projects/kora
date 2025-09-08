package ru.tinkoff.kora.aop.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractTestAnnotationAspect implements KoraAspect {
    protected abstract ClassName testAnnotation();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(testAnnotation().canonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        var annotation = executableElement.getParameters().stream()
            .map(v -> AnnotationUtils.findAnnotation(v, testAnnotation()))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(executableElement, testAnnotation());
        }
        if (annotation == null) {
            annotation = Objects.requireNonNull(AnnotationUtils.findAnnotation(executableElement.getEnclosingElement(), testAnnotation()));
        }
        var field = aspectContext.fieldFactory().constructorParam(
            ClassName.get(TestMethodCallListener.class),
            List.of()
        );
        var annotationValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
        var b = CodeBlock.builder()
            .addStatement("this.$N.before($S)", field, annotationValue);
        if (!executableElement.getReturnType().getKind().equals(TypeKind.VOID)) {
            b.addStatement("$T _result = null", TypeName.get(executableElement.getReturnType()).box())
                .beginControlFlow("try")
                .addStatement("_result = $L($L)", superCall, executableElement.getParameters().stream().map(e -> CodeBlock.of("$N", e.getSimpleName())).collect(CodeBlock.joining(",")))
                .addStatement("this.$N.after($S, _result)", field, annotationValue);
            b.addStatement("return _result");
        } else {
                b.beginControlFlow("try")
                .addStatement("$L($L)", superCall, executableElement.getParameters().stream().map(e -> CodeBlock.of("$N", e.getSimpleName())).collect(CodeBlock.joining(",")))
                .addStatement("this.$N.after($S, null)", field, annotationValue);
        }
        b.nextControlFlow("catch (Throwable e)")
            .addStatement("this.$N.thrown($S, e)", field, annotationValue)
            .addStatement("throw e")
            .endControlFlow();

        return new ApplyResult.MethodBody(b.build());
    }
}
