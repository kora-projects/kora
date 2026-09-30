package ru.tinkoff.kora.kora.app.annotation.processor.component;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclarations;

import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Resolved components in resolution order (position == {@link ResolvedComponent#index()})
 * with lookups by declaration and by "raw type name -> components" index.
 */
public final class ResolvedComponents {
    private final ProcessingContext ctx;
    private final List<ResolvedComponent> components;
    private final Map<ComponentDeclaration, ResolvedComponent> declarationToComponent;
    private final Map<String, List<ResolvedComponent>> typeToComponents;
    // components whose type hierarchy can't be indexed reliably: they are candidates for every type
    private final List<ResolvedComponent> notIndexed;

    public ResolvedComponents(ProcessingContext ctx) {
        this.ctx = ctx;
        this.components = new ArrayList<>(256);
        this.declarationToComponent = new IdentityHashMap<>(256);
        this.typeToComponents = new HashMap<>();
        this.notIndexed = new ArrayList<>();
    }

    public ResolvedComponents(ResolvedComponents that) {
        this.ctx = that.ctx;
        this.components = new ArrayList<>(that.components);
        this.declarationToComponent = new IdentityHashMap<>(that.declarationToComponent);
        this.typeToComponents = new HashMap<>(that.typeToComponents.size());
        for (var entry : that.typeToComponents.entrySet()) {
            this.typeToComponents.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.notIndexed = new ArrayList<>(that.notIndexed);
    }

    public void add(ResolvedComponent component) {
        this.components.add(component);
        this.declarationToComponent.putIfAbsent(component.declaration(), component);
        var keys = ComponentDeclarations.indexKeys(this.ctx, component.type());
        if (keys == null) {
            this.notIndexed.add(component);
        } else {
            for (var key : keys) {
                this.typeToComponents.computeIfAbsent(key, k -> new ArrayList<>()).add(component);
            }
        }
    }

    public int size() {
        return this.components.size();
    }

    public ResolvedComponent get(int index) {
        return this.components.get(index);
    }

    public List<ResolvedComponent> components() {
        return Collections.unmodifiableList(this.components);
    }

    /**
     * @return first resolved component with exactly this declaration (by reference)
     */
    @Nullable
    public ResolvedComponent getByDeclaration(ComponentDeclaration declaration) {
        return this.declarationToComponent.get(declaration);
    }

    /**
     * @return components that may be assignable to the given type, in resolution order
     */
    public List<ResolvedComponent> getByType(TypeMirror type) {
        var key = ComponentDeclarations.lookupKey(type);
        if (key == null) {
            return this.components();
        }
        var indexed = this.typeToComponents.getOrDefault(key, List.of());
        if (this.notIndexed.isEmpty()) {
            return Collections.unmodifiableList(indexed);
        }
        var result = new ArrayList<ResolvedComponent>(indexed.size() + this.notIndexed.size());
        result.addAll(indexed);
        result.addAll(this.notIndexed);
        result.sort(Comparator.comparingInt(ResolvedComponent::index));
        return result;
    }
}
