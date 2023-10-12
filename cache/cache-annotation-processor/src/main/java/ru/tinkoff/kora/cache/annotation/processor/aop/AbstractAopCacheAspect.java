package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.lang.model.element.ExecutableElement;

import static com.squareup.javapoet.CodeBlock.joining;

abstract class AbstractAopCacheAspect implements KoraAspect {

    String getSuperMethod(ExecutableElement method, String superCall) {
        return method.getParameters().stream()
            .map(p -> CodeBlock.of("$L", p))
            .collect(joining(", ", superCall + "(", ")")).toString();
    }
}
