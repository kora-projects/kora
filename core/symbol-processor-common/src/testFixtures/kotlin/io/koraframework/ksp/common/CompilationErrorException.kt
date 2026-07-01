package io.koraframework.ksp.common

class CompilationErrorException(val messages: List<String>) : RuntimeException(messages.joinToString("\n"))
