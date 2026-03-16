package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.ClassName;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.DeclarationWithIndex;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class ResolvedComponents {
    private final Map<Integer, ResolvedComponent> declarationIndexToResolvedComponent = new LinkedHashMap<>();
    private ResolvedComponent[] resolvedComponents = new ResolvedComponent[10];
    private int currentIdx = 0;

    public ResolvedComponents() {
    }

    public ResolvedComponents(ResolvedComponents that) {
        this.declarationIndexToResolvedComponent.putAll(that.declarationIndexToResolvedComponent);
        this.resolvedComponents = Arrays.copyOf(that.resolvedComponents, that.resolvedComponents.length);
        this.currentIdx = that.currentIdx;
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
        var component = new ResolvedComponent(
            declarationIndexToResolvedComponent.size(), declaration, declaration.type(), declaration.tag(),
            List.of(), // TODO,
            resolvedDependencies
        );
        declarationIndexToResolvedComponent.put(declarationIdx, component);
        if (currentIdx >= resolvedComponents.length) {
            resolvedComponents = Arrays.copyOf(resolvedComponents, resolvedComponents.length * 2);
        }
        this.resolvedComponents[currentIdx] = component;
        currentIdx++;
    }

    public void processConditions(Map<ClassName, ResolvedComponent> conditions) {
        class Helper {
            static int maxDependencyIndex(ComponentDependency dependency) {
                return switch (dependency) {
                    case ComponentDependency.PromisedProxyParameterDependency _,
                         ComponentDependency.PromiseOfDependency _,
                         ComponentDependency.TypeOfDependency _,
                         ComponentDependency.NullDependency _ -> 0;
                    case ComponentDependency.AllOfDependency allOfDependency -> {
                        var max = 0;
                        for (var d : allOfDependency.getResolvedDependencies()) {
                            max = Math.max(max, maxDependencyIndex(d));
                        }
                        yield max;
                    }
                    case ComponentDependency.OneOfDependency oneOfDependency -> {
                        var max = 0;
                        for (var d : oneOfDependency.dependencies()) {
                            max = Math.max(max, maxDependencyIndex(d));
                        }
                        yield max;
                    }
                    case ComponentDependency.TargetDependency targetDependency -> targetDependency.component().index();
                    case ComponentDependency.ValueOfDependency valueOfDependency -> valueOfDependency.component().index();
                    case ComponentDependency.WrappedTargetDependency wrappedTargetDependency -> wrappedTargetDependency.component().index();
                };

            }
        }
        for (var condition : conditions.values()) {
            var conditionIndex = condition.index();
            if (conditionIndex == 0) {
                continue;
            }
            if (condition.dependencies().isEmpty()) {
                System.arraycopy(resolvedComponents, 0, resolvedComponents, 1, conditionIndex);
                resolvedComponents[0] = condition;
                for (int i = 0; i <= conditionIndex; i++) {
                    resolvedComponents[i].setIndex(i);
                }
                return;
            }
            var maxDependency = condition.dependencies()
                .stream()
                .map(Helper::maxDependencyIndex)
                .min(Comparator.naturalOrder())
                .orElse(-1);
            assert conditionIndex > maxDependency;
            System.arraycopy(resolvedComponents, maxDependency + 1, resolvedComponents, maxDependency + 2, conditionIndex - maxDependency);
            resolvedComponents[maxDependency + 1] = condition;
            for (int i = maxDependency + 1; i <= conditionIndex; i++) {
                resolvedComponents[i].setIndex(i);
            }
        }
    }

    public List<ResolvedComponent> components() {
        return Collections.unmodifiableList(Arrays.asList(this.resolvedComponents)).subList(0, currentIdx);
    }

    public int size() {
        return declarationIndexToResolvedComponent.size();
    }
}
