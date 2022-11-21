package ru.tinkoff.kora.config.annotation.processor.exception;

import javax.lang.model.element.TypeElement;

public class NewRoundWantedException extends RuntimeException {
    private final TypeElement element;

    public NewRoundWantedException(TypeElement element) {
        super();
        this.element = element;
    }

    public TypeElement getElement() {
        return element;
    }
}
