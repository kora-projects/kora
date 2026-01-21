package ru.tinkoff.kora.kora.app.annotation.processor.component;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.DeclarationWithIndex;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResolvedComponents {
    private final Map<Integer, ResolvedComponent> declarationIndexToResolvedComponent = new LinkedHashMap<>();

    public ResolvedComponents() {
    }

    public ResolvedComponents(ResolvedComponents that) {
        this.declarationIndexToResolvedComponent.putAll(that.declarationIndexToResolvedComponent);
    }

    @Nullable
    public ResolvedComponent getByDeclarationIndex(int idx) {
        return declarationIndexToResolvedComponent.get(idx);
    }

    @Nullable
    public ResolvedComponent getByDeclaration(DeclarationWithIndex d) {
        return declarationIndexToResolvedComponent.get(d.index());
    }

    public void add(int declarationIdx, ComponentDeclaration declaration, List<ComponentDependency> resolvedDependencies) {
        declarationIndexToResolvedComponent.put(declarationIdx, new ResolvedComponent(
            declarationIndexToResolvedComponent.size(), declaration, declaration.type(), declaration.tag(),
            List.of(), // TODO,
            resolvedDependencies
        ));
    }

    public Collection<ResolvedComponent> components() {
        return declarationIndexToResolvedComponent.values();
    }

    public int size() {
        return declarationIndexToResolvedComponent.size();
    }
}
