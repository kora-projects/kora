package ru.tinkoff.kora.http.server.annotation.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class HttpProcessorException extends RuntimeException {
    private final Element element;

    public HttpProcessorException(String message, Element element) {
        super(message, null);
        this.element = element;
    }

    public void printError(ProcessingEnvironment processingEnvironment) {
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, this.getMessage(), this.element);
    }
}