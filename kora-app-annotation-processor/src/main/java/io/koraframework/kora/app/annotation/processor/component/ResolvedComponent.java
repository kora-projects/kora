package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.CodeBlock;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import org.jspecify.annotations.Nullable;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Objects;

import static io.koraframework.kora.app.annotation.processor.KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS;

public record ResolvedComponent(int index, ComponentDeclaration declaration, TypeMirror type, @Nullable String tag, List<TypeMirror> templateParams, List<ComponentDependency> dependencies) {
    public ResolvedComponent {
        Objects.requireNonNull(declaration);
        Objects.requireNonNull(type);
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

    public CodeBlock nodeRef(String inHolder) {
        var holderName = holderName();
        if (inHolder.equals(holderName)) {
            return CodeBlock.of("$N", fieldName());
        } else {
            return CodeBlock.of("$N.$N", holderName, fieldName());
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResolvedComponent[");
        sb.append("index=").append(index);
        sb.append(", declaration=").append(declaration);
        sb.append(", type=").append(type);
        if (tag != null) {
            sb.append(", tag=").append(tag);
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
