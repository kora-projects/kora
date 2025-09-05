package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import com.palantir.javapoet.CodeBlock;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public sealed interface ExtensionResult {

    static GeneratedResult fromExecutable(ExecutableElement constructor) {
        return new ExtensionResult.GeneratedResult(constructor, (ExecutableType) constructor.asType());
    }

    static GeneratedResult fromExecutable(ExecutableElement executableElement, ExecutableType executableType) {
        return new ExtensionResult.GeneratedResult(executableElement, executableType);
    }

    record GeneratedResult(ExecutableElement sourceElement, ExecutableType targetType) implements ExtensionResult {}

    record CodeBlockResult(Element source, Function<CodeBlock, CodeBlock> codeBlock, TypeMirror componentType, Set<String> componentTag, List<TypeMirror> dependencyTypes,
                           List<Set<String>> dependencyTags) implements ExtensionResult {
    }
}
