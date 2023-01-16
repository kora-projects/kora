package ru.tinkoff.kora.validation.annotation.processor;

import javax.lang.model.element.Element;

class ValidationElementException extends RuntimeException {

    private final Element element;

    public ValidationElementException(String message, Element element) {
        super(message);
        this.element = element;
    }

    public Element getElement() {
        return element;
    }
}
