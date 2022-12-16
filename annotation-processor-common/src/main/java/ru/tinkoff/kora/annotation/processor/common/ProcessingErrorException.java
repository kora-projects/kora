package ru.tinkoff.kora.annotation.processor.common;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessingErrorException extends RuntimeException {
    private final List<ProcessingError> errors;

    public ProcessingErrorException(List<ProcessingError> errors) {
        super(toMessage(errors));
        this.errors = errors;
    }

    public ProcessingErrorException(ProcessingError error) {
        this(List.of(error));
    }

    public ProcessingErrorException(String message, Element element, AnnotationMirror a, AnnotationValue v) {
        this(List.of(new ProcessingError(Diagnostic.Kind.ERROR, message, element, a, v)));
    }

    public ProcessingErrorException(String message, Element element, AnnotationMirror a) {
        this(List.of(new ProcessingError(message, element, a)));
    }

    public ProcessingErrorException(String message, Element element) {
        this(List.of(new ProcessingError(message, element)));
    }


    private static String toMessage(List<ProcessingError> errors) {
        return errors.stream()
            .map(ProcessingError::message)
            .collect(Collectors.joining("\n"));
    }

    public List<ProcessingError> getErrors() {
        return errors;
    }

    public static ProcessingErrorException merge(List<ProcessingErrorException> exceptions) {
        var errors = exceptions.stream()
            .map(ProcessingErrorException::getErrors)
            .flatMap(Collection::stream)
            .toList();

        var exception = new ProcessingErrorException(errors);
        for (var processingErrorException : exceptions) {
            exception.addSuppressed(processingErrorException);
        }
        return exception;
    }

    public void printError(ProcessingEnvironment processingEnv) {
        this.printError(0, processingEnv);
    }

    private void printError(int indent, ProcessingEnvironment processingEnv) {
        for (var error : this.errors) {
            error.print(indent, processingEnv);
        }
        for (var supressed : this.getSuppressed()) {
            if (supressed instanceof ProcessingErrorException e) {
                e.printError(indent + 6, processingEnv);
            }
        }
    }
}
