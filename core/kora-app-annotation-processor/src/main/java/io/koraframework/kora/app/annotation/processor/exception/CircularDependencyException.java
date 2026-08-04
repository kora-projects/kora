package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import java.util.List;
import java.util.stream.Collectors;

public class CircularDependencyException extends ProcessingErrorException {

    public CircularDependencyException(List<ComponentDeclaration> cycle, ComponentDeclaration declaration) {
        super(getError(cycle, declaration));
    }

    private static ProcessingError getError(List<ComponentDeclaration> cycle,
                                            ComponentDeclaration declaration) {
        var deps = cycle.stream()
            .map(ComponentDeclaration::declarationString)
            .collect(Collectors.joining("\n  ^--- ", "Dependency cycle:\n  @--- ", "")).indent(2);

        var msg = new StringBuilder();
        msg.append("Circular dependency found:\n  ").append(TypeName.get(declaration.type()));
        if (declaration.tag() == null) {
            msg.append(" (no tags)");
        } else {
            msg.append(" with @Tag(").append(declaration.tag()).append(".class)");
        }
        msg.append("\n\n").append(deps.stripTrailing());
        msg.append(" [CYCLE]");
        msg.append("\n\nFix:");
        msg.append("\n  - Break the cycle with ValueOf<T> or PromiseOf<T> where lazy access is valid.");
        msg.append("\n  - Move shared state into a separate component.");
        msg.append("\n  - Do not create dependency cycles in ").append(CommonClassNames.lifecycle).append('.');
        return new ProcessingError(msg.toString(), declaration.source());
    }
}
