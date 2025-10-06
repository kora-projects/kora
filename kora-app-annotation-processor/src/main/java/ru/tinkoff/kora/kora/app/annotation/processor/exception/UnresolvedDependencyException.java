package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingState;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

public class UnresolvedDependencyException extends ProcessingErrorException {

    private final ComponentDeclaration component;
    private final DependencyClaim dependencyClaim;
    private final Deque<ProcessingState.ResolutionFrame> stack;

    public UnresolvedDependencyException(String message,
                                         ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         Deque<ProcessingState.ResolutionFrame> stack) {
        this(message, component, dependencyClaim, List.of(), stack);
    }

    public UnresolvedDependencyException(String message,
                                         ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         List<ProcessingError> errors,
                                         Deque<ProcessingState.ResolutionFrame> stack) {
        this(component, dependencyClaim, Stream.concat(Stream.of(new ProcessingError(message, component.source())), errors.stream()).toList(), stack);
    }

    public UnresolvedDependencyException(ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         List<ProcessingError> errors,
                                         Deque<ProcessingState.ResolutionFrame> stack) {
        super(errors);
        this.component = component;
        this.dependencyClaim = dependencyClaim;
        this.stack = stack;
    }

    public ComponentDeclaration getComponent() {
        return component;
    }

    public DependencyClaim getDependencyClaim() {
        return dependencyClaim;
    }

    public Deque<ProcessingState.ResolutionFrame> getStack() {
        return stack;
    }
}
