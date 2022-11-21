package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingState;

import javax.lang.model.type.TypeMirror;
import java.util.Set;

public class NewRoundException extends RuntimeException {
    private final ProcessingState.Processing processing;
    private final Object source;
    private final TypeMirror type;
    private final Set<String> tag;

    public NewRoundException(ProcessingState.Processing processing, Object source, TypeMirror type, Set<String> tag) {
        this.processing = processing;
        this.source = source;
        this.type = type;
        this.tag = tag;
    }

    public ProcessingState.Processing getResolving() {
        return processing;
    }

    public Object getSource() {
        return source;
    }

    public TypeMirror getType() {
        return type;
    }

    public Set<String> getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return "NewRoundException{" +
               "processing=" + processing +
               ", source=" + source +
               ", type=" + type +
               ", tag=" + tag +
               '}';
    }
}
