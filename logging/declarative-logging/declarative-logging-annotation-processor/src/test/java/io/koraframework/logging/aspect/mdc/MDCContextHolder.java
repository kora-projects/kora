package io.koraframework.logging.aspect.mdc;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;

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
