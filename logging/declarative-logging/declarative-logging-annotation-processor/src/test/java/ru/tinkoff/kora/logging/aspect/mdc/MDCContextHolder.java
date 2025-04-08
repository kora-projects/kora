package ru.tinkoff.kora.logging.aspect.mdc;

import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.Collections;
import java.util.Map;

public class MDCContextHolder {

    private Map<String, StructuredArgumentWriter> mdcContext;

    public Map<String, StructuredArgumentWriter> get() {
        return mdcContext;
    }

    public void set(Map<String, StructuredArgumentWriter> mdcContext) {
        this.mdcContext = mdcContext == null
            ? Collections.emptyMap()
            : Map.copyOf(mdcContext);
    }
}
