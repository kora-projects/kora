package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import java.util.List;
import java.util.stream.Collectors;

public class CircularDependencyException extends ProcessingErrorException {

    public CircularDependencyException(List<ComponentDeclaration> cycle, ComponentDeclaration declaration) {
        super(getError(cycle, declaration));
    }

    private static ProcessingError getError(List<ComponentDeclaration> cycle,
                                            ComponentDeclaration declaration) {
        var deps = cycle.stream()
            .map(c -> String.format("- %s", c.declarationString()))
            .collect(Collectors.joining("\n", "Cycle dependency candidates:\n", "")).indent(2);

        if (declaration.tags().isEmpty()) {
            return new ProcessingError("Encountered circular dependency in graph for source type: " + TypeName.get(declaration.type()) + " (no tags)\n"
                                       + deps
                                       + "\nPlease check that you are not using cycle dependency in %s, this is forbidden.".formatted(CommonClassNames.lifecycle),
                declaration.source());
        } else {
            var tagMsg = declaration.tags().stream()
                .collect(Collectors.joining(", ", "@Tag(", ")"));
            return new ProcessingError("Encountered circular dependency in graph for source type: " + TypeName.get(declaration.type()) + " with " + tagMsg + "\n"
                                       + deps
                                       + "\nPlease check that you are not using cycle dependency in %s, this is forbidden.".formatted(CommonClassNames.lifecycle),
                declaration.source());
        }
    }
}
