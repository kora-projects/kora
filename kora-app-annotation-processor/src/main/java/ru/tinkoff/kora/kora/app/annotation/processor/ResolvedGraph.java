package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record ResolvedGraph(TypeElement root, List<TypeElement> allModules, List<ResolvedComponent> components) {}
