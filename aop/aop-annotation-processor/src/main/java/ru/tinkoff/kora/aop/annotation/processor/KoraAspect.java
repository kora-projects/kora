package ru.tinkoff.kora.aop.annotation.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface KoraAspect {
    Set<String> getSupportedAnnotationTypes();

    interface FieldFactory {
        default String constructorParam(TypeMirror type, List<AnnotationSpec> annotations) {
            return this.constructorParam(TypeName.get(type), annotations);
        }

        String constructorParam(TypeName type, List<AnnotationSpec> annotations);

        default String constructorInitialized(TypeMirror type, CodeBlock initializer) {
            return this.constructorInitialized(TypeName.get(type), initializer);
        }

        String constructorInitialized(TypeName type, CodeBlock initializer);
    }

    sealed interface ApplyResult {
        enum Noop implements ApplyResult {INSTANCE}

        record MethodBody(CodeBlock codeBlock) implements ApplyResult {}
    }

    record AspectContext(TypeSpec.Builder typeBuilder, FieldFactory fieldFactory) {}

    ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext);

    /**
     * Generate calling of original method with params
     *
     * <p>Example result:</p>
     * <pre>{@code super.<originalMethodName>(param1, param2)}</pre>
     *
     * @return codeBlock
     */
    static CodeBlock callSuper(String superCall, List<String> params) {
        var b = CodeBlock.builder()
            .add(superCall)
            .add("(");
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            if (i > 0) {
                b.add(", ");
            }
            b.add("$N", param);
        }
        b.add(")");
        return b.build();
    }

    /**
     * Generate calling of original method with params
     *
     * <p>Example result:</p>
     * <pre>{@code super.<originalMethodName>(param1, param2)}</pre>
     *
     * @return codeBlock
     */
    static CodeBlock callSuper(ExecutableElement executableElement, String superCall) {
        var params = new ArrayList<String>(executableElement.getParameters().size());
        for (var parameter : executableElement.getParameters()) {
            params.add(parameter.getSimpleName().toString());
        }
        return callSuper(superCall, params);
    }
}
