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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResolvedComponent[");
        sb.append("index=").append(index);
        sb.append(", declaration=").append(declaration);
        sb.append(", type=").append(type);
        if (tags != null && !tags.isEmpty()) {
            sb.append(", tags=").append(tags);
        }
        if (templateParams != null && !templateParams.isEmpty()) {
            sb.append(", templateParams=").append(templateParams);
        }
        if (dependencies != null && !dependencies.isEmpty()) {
            sb.append(", dependencies=");
            for (int i = 0; i < dependencies.size(); i++) {
                ComponentDependency componentDependency = dependencies.get(i);
                sb.append(componentDependency);
                if (i + 1 < dependencies.size()) {
                    sb.append(", ");
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
