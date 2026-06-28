package io.koraframework.logging.symbol.processor.aop.mdc

import io.koraframework.logging.common.arg.StructuredArgumentWriter

class MDCContextHolder {

    private var mdcContext: Map<String, StructuredArgumentWriter>? = null

    fun get(): Map<String, StructuredArgumentWriter>? {
        return mdcContext
    }

    fun set(mdcContext: Map<String, StructuredArgumentWriter>?) {
        this.mdcContext = mdcContext?.toMap() ?: emptyMap()
    }
}
