package ru.tinkoff.kora.annotation.processor.common;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import javax.lang.model.element.Element;
import java.util.Collection;
import java.util.stream.Collectors;

public final class LogUtils {

    private LogUtils() {}

    public static void logElementsFull(Logger logger, Level level, String prefix, Collection<? extends Element> elements) {
        if (!elements.isEmpty() && logger.isEnabledForLevel(level)) {
            String out = elements.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"))
                .indent(4);

            logger.makeLoggingEventBuilder(level).log(prefix + ":\n{}", out);
        }
    }

    public static void logAnnotatedElementsFull(Logger logger, Level level, String prefix, Collection<? extends AbstractKoraProcessor.AnnotatedElement> elements) {
        if (!elements.isEmpty() && logger.isEnabledForLevel(level)) {
            String out = elements.stream()
                .map(AbstractKoraProcessor.AnnotatedElement::element)
                .map(Object::toString)
                .collect(Collectors.joining("\n"))
                .indent(4);

            logger.makeLoggingEventBuilder(level).log(prefix + ":\n{}", out);
        }
    }

    public static void logElementsSimple(Logger logger, Level level, String prefix, Collection<? extends Element> elements) {
        if (!elements.isEmpty() && logger.isEnabledForLevel(level)) {
            String out = elements.stream()
                .map(e -> e.getSimpleName().toString())
                .collect(Collectors.joining(", "))
                .indent(4);

            logger.makeLoggingEventBuilder(level).log(prefix + ":\n{}", out);
        }
    }
}
