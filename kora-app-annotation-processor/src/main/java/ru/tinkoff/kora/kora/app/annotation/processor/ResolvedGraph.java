package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponents;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclarations;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record ResolvedGraph(TypeElement root, List<TypeElement> allModules, ComponentDeclarations declarations, ResolvedComponents components) {}
