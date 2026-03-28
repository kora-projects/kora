package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import org.jspecify.annotations.Nullable;

import javax.lang.model.type.TypeMirror;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.koraframework.kora.app.annotation.processor.KoraAppProcessor.COMPONENTS_PER_HOLDER_CLASS;

public final class ResolvedComponent {
    private static final ClassName UNCONDITIONALLY = ClassName.get(ResolvedComponents.class);

    private int index;
    private final ComponentDeclaration declaration;
    private final TypeMirror type;
    private final @Nullable String tag;
    private final List<TypeMirror> templateParams;
    private final List<ComponentDependency> dependencies;
    private String fieldName;
    private String holderName;
    private final Set<ClassName> parentConditions = new HashSet<>();

    public ResolvedComponent(int index, ComponentDeclaration declaration, TypeMirror type, @Nullable String tag, List<TypeMirror> templateParams, List<ComponentDependency> dependencies) {
        this.index = index;
        this.declaration = Objects.requireNonNull(declaration);
        this.type = Objects.requireNonNull(type);
        this.tag = tag;
        this.templateParams = Objects.requireNonNull(templateParams);
        this.dependencies = Objects.requireNonNull(dependencies);
        this.fieldName = "component" + this.index;
        var holderNumber = this.index / COMPONENTS_PER_HOLDER_CLASS;
        this.holderName = "holder" + holderNumber;
    }

    public CodeBlock nodeRef(String inHolder) {
        if (inHolder.equals(holderName)) {
            return CodeBlock.of("$N", fieldName);
        } else {
            return CodeBlock.of("$N.$N", holderName, fieldName);
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

    public int index() {return index;}

    public ComponentDeclaration declaration() {return declaration;}

    public TypeMirror type() {return type;}

    public @Nullable String tag() {return tag;}

    public List<TypeMirror> templateParams() {return templateParams;}

    public List<ComponentDependency> dependencies() {return dependencies;}

    public String fieldName() {return fieldName;}

    public String holderName() {return holderName;}

    public Set<ClassName> parentConditions() {
        if (this.parentConditions.contains(UNCONDITIONALLY)) {
            return Set.of();
        }
        return parentConditions;
    }

    private void addParentCondition(Set<ClassName> conditions) {
        if (this.parentConditions.contains(UNCONDITIONALLY)) {
            return;
        }
        if (conditions.contains(UNCONDITIONALLY)) {
            this.parentConditions.clear();
            this.parentConditions.add(UNCONDITIONALLY);
            return;
        }
        this.parentConditions.addAll(conditions);
        if (this.declaration.condition() != null) {
            this.parentConditions.remove(this.declaration.condition());
        }
    }

    public void processCondition() {
        final Set<ClassName> condition;
        if (this.declaration.condition() == null && this.parentConditions.isEmpty()) {
            condition = Set.of(UNCONDITIONALLY);
        } else if (this.declaration.condition() == null) {
            condition = this.parentConditions;
        } else {
            condition = new HashSet<>(this.parentConditions);
            condition.add(this.declaration.condition());
            condition.remove(UNCONDITIONALLY);
        }

        for (var dependency : this.dependencies) {
            switch (dependency) {
                case ComponentDependency.NullDependency _ -> {}
                case ComponentDependency.TypeOfDependency _ -> {}
                case ComponentDependency.GraphDependency _ -> {}
                case ComponentDependency.PromisedProxyParameterDependency _ -> {}
                case ComponentDependency.PromiseOfDependency promiseOfDependency -> promiseOfDependency.component().addParentCondition(condition);
                case ComponentDependency.AllOfDependency allOfDependency -> {
                    for (var d : allOfDependency.getResolvedDependencies()) {
                        d.component().addParentCondition(condition);
                    }
                }
                case ComponentDependency.TargetDependency targetDependency -> targetDependency.component().addParentCondition(condition);
                case ComponentDependency.ValueOfDependency valueOfDependency -> valueOfDependency.component().addParentCondition(condition);
                case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> wrappedTargetDependency.component().addParentCondition(condition);
                case ComponentDependency.OneOfDependency oneOfDependency -> {
                    for (var d : oneOfDependency.dependencies()) {
                        d.component().addParentCondition(condition);
                    }
                }
            }
        }
    }

    public void setIndex(int i) {
        this.index = i;
        this.fieldName = "component" + this.index;
        var holderNumber = this.index / COMPONENTS_PER_HOLDER_CLASS;
        this.holderName = "holder" + holderNumber;
    }
}
