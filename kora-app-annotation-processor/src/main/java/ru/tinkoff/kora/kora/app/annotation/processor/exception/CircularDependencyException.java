package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import java.util.List;

public class CircularDependencyException extends ProcessingErrorException {
    public CircularDependencyException(List<String> cycle, ComponentDeclaration source) {
        super(String.format("There's a cycle in graph: \n\t%s", String.join("\n\t", cycle)), source.source());
    }
}
