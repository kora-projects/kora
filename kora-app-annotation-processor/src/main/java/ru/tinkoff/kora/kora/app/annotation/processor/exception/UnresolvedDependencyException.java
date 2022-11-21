package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class UnresolvedDependencyException extends ProcessingErrorException {
    private final Element forElement;
    private final TypeMirror missingType;
    private final Set<String> missingTag;

    public UnresolvedDependencyException(String message, Element forElement, TypeMirror missingType, Set<String> tag) {
        this(message + "\n" + evaluateMessage(forElement), forElement, missingType, tag, List.of());
    }

    public UnresolvedDependencyException(ProcessingError error, TypeMirror missingType, Set<String> tag) {
        this(error.message(), error.element(), missingType, tag, List.of());
    }

    public UnresolvedDependencyException(String message, Element forElement, TypeMirror missingType, Set<String> missingTag, List<ProcessingError> errors) {
        this(forElement, missingType, missingTag, Stream.concat(Stream.of(new ProcessingError(message, forElement)), errors.stream()).toList());
    }

    public UnresolvedDependencyException(Element forElement, TypeMirror missingType, Set<String> missingTag, List<ProcessingError> errors) {
        super(errors);
        this.forElement = forElement;
        this.missingType = missingType;
        this.missingTag = missingTag;
    }

    private static String evaluateMessage(Element element) {
        ExecutableElement factoryMethod = null;
        TypeElement module = null;
        do {
            if (element instanceof ExecutableElement) {
                factoryMethod = (ExecutableElement) element;
            } else if (element instanceof TypeElement) {
                module = (TypeElement) element;
                break;
            } else if (element == null) {
                continue;
            }
            element = element.getEnclosingElement();
        } while (element != null);
        return "Requested at: %s.%s".formatted(module, factoryMethod);
    }

    public Element getForElement() {
        return forElement;
    }

    public TypeMirror getMissingType() {
        return missingType;
    }

    public Set<String> getMissingTag() {
        return missingTag;
    }
}
