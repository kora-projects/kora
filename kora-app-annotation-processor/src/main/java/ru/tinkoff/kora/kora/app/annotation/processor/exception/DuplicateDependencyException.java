package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

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
            .collect(Collectors.joining("\n", "Candidates for injection:\n", "")).indent(2);

        return getError(claim, declaration, deps);
    }

    private static ProcessingError getErrorForDependencies(DependencyClaim claim,
                                                           ComponentDeclaration declaration,
                                                           List<ComponentDependency.SingleDependency> foundDeclarations) {
        var deps = foundDeclarations.stream()
            .map(ComponentDependency.SingleDependency::component)
            .map(c -> String.format("- %s", c.declaration().declarationString()))
            .collect(Collectors.joining("\n", "Candidates for injection:\n", "")).indent(2);

        return getError(claim, declaration, deps);
    }

    private static ProcessingError getError(DependencyClaim claim,
                                            ComponentDeclaration declaration,
                                            String deps) {
        if (claim.tag() == null) {
            return new ProcessingError("More than one declaration matches dependency type: " + TypeName.get(claim.type()) + " (no tags)\n"
                                       + deps
                                       + "\nPlease check that injection dependency is declared correctly or that @DefaultComponent annotation is not missing if was intended.",
                declaration.source());
        } else {
            var tagMsg = "@Tag(" + claim.tag() + ".class)";
            return new ProcessingError("More than one declaration matches dependency type: " + TypeName.get(claim.type()) + " with " + tagMsg + "\n"
                                       + deps
                                       + "\nPlease check that injection dependency is declared correctly or that @DefaultComponent annotation is not missing if was intended.",
                declaration.source());
        }
    }
}
