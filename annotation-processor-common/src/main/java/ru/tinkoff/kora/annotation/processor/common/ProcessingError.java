package ru.tinkoff.kora.annotation.processor.common;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public record ProcessingError(Diagnostic.Kind kind, String message, Element element, AnnotationMirror a, AnnotationValue v) {
    public ProcessingError(String message, Element element, AnnotationMirror a) {
        this(Diagnostic.Kind.ERROR, message, element, a, null);
    }

    public ProcessingError(String message, Element element) {
        this(Diagnostic.Kind.ERROR, message, element, null, null);
    }

    public ProcessingError(Diagnostic.Kind kind, String message, Element element) {
        this(kind, message, element, null, null);
    }

    public void print(ProcessingEnvironment processingEnvironment) {
        var element = this.element();

        if (element != null) for (var enclosedElement : element.getEnclosingElement().getEnclosedElements()) {
            if (processingEnvironment.getTypeUtils().isSameType(element.asType(), enclosedElement.asType())) {
                element = enclosedElement;
            }
        }

        processingEnvironment.getMessager().printMessage(kind, this.message(), element, this.a(), this.v());
    }


    public static ProcessingError merge(ProcessingError e1, ProcessingError e2) {
        if (e1.message().isEmpty()) {
            return e2;
        }
        if (e2.message().isEmpty()) {
            return e1;
        }
        ProcessingError root;
        ProcessingError child;

        if (e1.kind() == Diagnostic.Kind.ERROR) {
            root = e1;
            child = e2;
        } else {
            child = e1;
            root = e2;
        }
        return new ProcessingError(
            root.kind(), root.message() + "\n" + child.message(), root.element(), root.a(), root.v()
        );
    }

    public ProcessingError indent(int i) {
        return new ProcessingError(
            kind, message.indent(i), element, a, v
        );
    }
}
