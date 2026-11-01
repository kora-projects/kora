package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.MethodSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Methods generated next to the component holder constructor: long lists of nodes are built there in chunks,
 * because neither holder constructor nor component factory can be larger than 64KB of bytecode
 */
public final class GraphHelperMethods {
    /**
     * Lists of this size and bigger are built by helper methods, it is also the number of elements added by a single method
     */
    public static final int WIDE_LIST_SIZE = 500;

    private final List<MethodSpec> methods = new ArrayList<>();
    private int counter = 0;

    public String nextName(String prefix) {
        return prefix + this.counter++;
    }

    public void add(MethodSpec method) {
        this.methods.add(method);
    }

    public List<MethodSpec> methods() {
        return Collections.unmodifiableList(this.methods);
    }
}
