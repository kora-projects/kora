package io.koraframework.kora.app.annotation.processor.declaration;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.TypeElement;

public sealed interface ModuleDeclaration {
    TypeElement element();

    record MixedInModule(TypeElement element) implements ModuleDeclaration {}

    record AnnotatedModule(TypeElement element) implements ModuleDeclaration {}

    record ClassModule(TypeElement element) implements ModuleDeclaration {}

    record FactoryModule(TypeElement element, @Nullable String tag) implements ModuleDeclaration {}
}
