package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.component.ComponentDependency;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.stream.Collectors;

public class DuplicateDependencyException extends ProcessingErrorException {

    public DuplicateDependencyException(DependencyClaim claim,
                                        ComponentDeclaration declaration,
                                        List<ComponentDeclaration> foundDeclarations) {
        super(List.of(getErrorForDeclarations(claim, declaration, foundDeclarations)));
    }

    public DuplicateDependencyException(List<ComponentDependency.SingleDependency> foundDeclarations,
                                        DependencyClaim claim,
                                        ComponentDeclaration declaration) {
        super(List.of(getErrorForDependencies(claim, declaration, foundDeclarations)));
    }

    private static ProcessingError getErrorForDeclarations(DependencyClaim claim,
                                                           ComponentDeclaration declaration,
                                                           List<ComponentDeclaration> foundDeclarations) {
        var deps = foundDeclarations.stream()
            .map(c -> String.format("- %s", c.declarationString()))
            .collect(Collectors.joining("\n", "Candidates:\n", "")).indent(2);

        return getError(claim, declaration, deps);
    }

    private static ProcessingError getErrorForDependencies(DependencyClaim claim,
                                                           ComponentDeclaration declaration,
                                                           List<ComponentDependency.SingleDependency> foundDeclarations) {
        var deps = foundDeclarations.stream()
            .map(ComponentDependency.SingleDependency::component)
            .map(c -> String.format("- %s", c.declaration().declarationString()))
            .collect(Collectors.joining("\n", "Candidates:\n", "")).indent(2);

        return getError(claim, declaration, deps);
    }

    private static ProcessingError getError(DependencyClaim claim,
                                            ComponentDeclaration declaration,
                                            String deps) {
        var msg = new StringBuilder();
        msg.append("Multiple components match dependency:\n  ").append(TypeName.get(claim.type()));
        if (claim.tag() == null) {
            msg.append(" (no tags)");
        } else {
            msg.append(" with @Tag(").append(claim.tag()).append(".class)");
        }
        var source = claim.source();
        if (source instanceof VariableElement variableElement) {
            msg.append("\n\nRequired at:\n  ")
                .append(variableElement.getEnclosingElement())
                .append("\n  parameter: ")
                .append(variableElement.asType())
                .append(" ")
                .append(variableElement.getSimpleName());
        }
        msg.append("\n\n").append(deps.stripTrailing());
        msg.append("\n\nFix:");
        msg.append("\n  - Add different @Tag(...) annotations to candidates and request the needed tag.");
        msg.append("\n  - Mark fallback candidate with @DefaultComponent.");
        msg.append("\n  - Remove one duplicate provider.");
        return new ProcessingError(msg.toString(), claim.source() == null ? declaration.source() : claim.source());
    }
}
