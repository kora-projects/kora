package ru.tinkoff.kora.aop.annotation.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

public interface KoraAspect {
    Set<String> getSupportedAnnotationTypes();

    interface FieldFactory {

        String constructorParam(TypeMirror type, List<AnnotationSpec> annotations);

        String constructorInitialized(TypeMirror type, CodeBlock initializer);
    }

    sealed interface ApplyResult {
        enum Noop implements ApplyResult {INSTANCE}

        record MethodBody(CodeBlock codeBlock) implements ApplyResult {}
    }
    record AspectContext(FieldFactory fieldFactory){}

    ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext);
}
