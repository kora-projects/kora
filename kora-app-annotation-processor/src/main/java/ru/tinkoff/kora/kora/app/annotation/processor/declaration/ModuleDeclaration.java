package ru.tinkoff.kora.kora.app.annotation.processor.declaration;

import javax.lang.model.element.TypeElement;

public interface ModuleDeclaration {
    TypeElement element();

    record MixedInModule(TypeElement element) implements ModuleDeclaration {}

    record AnnotatedModule(TypeElement element) implements ModuleDeclaration {}
}
