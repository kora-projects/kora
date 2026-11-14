package io.koraframework.resilient.annotation.processor.aop;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

final class ResilientAopUtils {
    private ResilientAopUtils() {}

    /**
     * Resilient contracts accept a lambda with a single exception type parameter,
     * so for a method that declares several unrelated checked exceptions javac infers their common supertype (e.g. Exception),
     * which the proxy method does not declare. In that case the call is wrapped with an explicit rethrow of the declared exceptions.
     */
    static CodeBlock rethrowDeclaredExceptions(ProcessingEnvironment env, ExecutableElement method, CodeBlock body) {
        var types = env.getTypeUtils();
        var runtimeException = env.getElementUtils().getTypeElement(RuntimeException.class.getCanonicalName()).asType();
        var error = env.getElementUtils().getTypeElement(Error.class.getCanonicalName()).asType();

        var checked = method.getThrownTypes().stream()
            .filter(t -> !types.isAssignable(t, runtimeException) && !types.isAssignable(t, error))
            .map(t -> (TypeMirror) t)
            .toList();
        if (mostGeneral(env, checked).size() <= 1) {
            return body;
        }

        var catchTypes = new ArrayList<TypeMirror>(checked);
        catchTypes.add(runtimeException);
        catchTypes.add(error);
        var multiCatch = mostGeneral(env, catchTypes).stream()
            .map(t -> CodeBlock.of("$T", TypeName.get(t)))
            .collect(CodeBlock.joining(" | "));

        return CodeBlock.builder()
            .beginControlFlow("try")
            .add(body)
            .nextControlFlow("catch ($L _e)", multiCatch)
            .addStatement("throw _e")
            .nextControlFlow("catch (Throwable _e)")
            .addStatement("throw new IllegalStateException(_e)")
            .endControlFlow()
            .build();
    }

    private static List<TypeMirror> mostGeneral(ProcessingEnvironment env, List<TypeMirror> thrownTypes) {
        var types = env.getTypeUtils();
        var result = new ArrayList<TypeMirror>();
        for (var type : thrownTypes) {
            if (thrownTypes.stream().noneMatch(other -> !types.isSameType(type, other) && types.isAssignable(type, other))
                && result.stream().noneMatch(r -> types.isSameType(r, type))) {
                result.add(type);
            }
        }
        return result;
    }
}
