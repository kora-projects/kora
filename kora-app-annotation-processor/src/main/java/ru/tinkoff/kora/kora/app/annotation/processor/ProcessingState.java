package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public sealed interface ProcessingState {
    sealed interface ResolutionFrame {
        record Root(int rootIndex) implements ResolutionFrame {}

        record Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind, List<ComponentDependency> resolvedDependencies, int currentDependency) implements ResolutionFrame {
            public Component(ComponentDeclaration declaration, List<DependencyClaim> dependenciesToFind) {
                this(declaration, dependenciesToFind, new ArrayList<>(dependenciesToFind.size()), 0);
            }

            public Component withCurrentDependency(int currentDependency) {
                return new Component(declaration, dependenciesToFind, resolvedDependencies, currentDependency);
            }
        }
    }

    record None(TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates, List<ComponentDeclaration> rootSet) implements ProcessingState {}

    record Processing(TypeElement root, List<TypeElement> allModules, List<ComponentDeclaration> sourceDeclarations, List<ComponentDeclaration> templates, List<ComponentDeclaration> rootSet,
                      List<ResolvedComponent> resolvedComponents, Deque<ResolutionFrame> resolutionStack) implements ProcessingState {

        @Nullable
        public ResolvedComponent findResolvedComponent(ComponentDeclaration declaration) {
            for (var resolvedComponent : this.resolvedComponents()) {
                if (declaration == resolvedComponent.declaration()) {
                    return resolvedComponent;
                }
            }
            return null;
        }
    }

    record Ok(TypeElement root, List<TypeElement> allModules, List<ResolvedComponent> components) implements ProcessingState {}

    record NewRoundRequired(Object source, TypeMirror type, Set<String> tag, Processing processing) implements ProcessingState {}

    record Failed(ProcessingErrorException detailedException) implements ProcessingState {}
}
