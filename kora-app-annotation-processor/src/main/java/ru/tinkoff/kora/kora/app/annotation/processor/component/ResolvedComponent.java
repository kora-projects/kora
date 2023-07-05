package ru.tinkoff.kora.kora.app.annotation.processor.component;

import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS;

public record ResolvedComponent(int index, ComponentDeclaration declaration, TypeMirror type, Set<String> tags, List<TypeMirror> templateParams, List<ComponentDependency> dependencies) {
    public ResolvedComponent {
        Objects.requireNonNull(declaration);
        Objects.requireNonNull(type);
        Objects.requireNonNull(tags);
        Objects.requireNonNull(templateParams);
        Objects.requireNonNull(dependencies);
    }

    public String fieldName() {
        return "component" + this.index;
    }

    public String holderName() {
        var holderNumber = this.index / COMPONENTS_PER_HOLDER_CLASS;
        return "holder" + holderNumber;
    }
}
