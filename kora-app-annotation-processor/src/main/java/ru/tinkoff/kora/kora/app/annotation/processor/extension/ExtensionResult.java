package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;

public sealed interface ExtensionResult {

    static GeneratedResult fromExecutable(ExecutableElement constructor) {
        return new ExtensionResult.GeneratedResult(constructor, (ExecutableType) constructor.asType());
    }

    static GeneratedResult fromExecutable(ExecutableElement executableElement, ExecutableType executableType) {
        return new ExtensionResult.GeneratedResult(executableElement, executableType);
    }

    static ExtensionResult nextRound() {
        return RequiresCompilingResult.INSTANCE;
    }

    record GeneratedResult(ExecutableElement sourceElement, ExecutableType targetType) implements ExtensionResult {}

    enum RequiresCompilingResult implements ExtensionResult {
        INSTANCE
    }
}
