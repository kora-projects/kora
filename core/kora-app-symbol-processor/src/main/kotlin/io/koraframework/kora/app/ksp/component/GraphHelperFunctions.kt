package io.koraframework.kora.app.ksp.component

import com.squareup.kotlinpoet.FunSpec

/**
 * Functions generated next to the component holder constructor: long lists of nodes are built there in chunks,
 * because neither holder constructor nor component factory can be larger than 64KB of bytecode
 */
class GraphHelperFunctions {
    private val functions = ArrayList<FunSpec>()
    private var counter = 0

    fun nextName(prefix: String): String {
        return prefix + this.counter++
    }

    fun add(function: FunSpec) {
        this.functions.add(function)
    }

    fun functions(): List<FunSpec> = this.functions

    companion object {
        /**
         * Lists of this size and bigger are built by helper functions, it is also the number of elements added by a single function
         */
        const val WIDE_LIST_SIZE = 500
    }
}
