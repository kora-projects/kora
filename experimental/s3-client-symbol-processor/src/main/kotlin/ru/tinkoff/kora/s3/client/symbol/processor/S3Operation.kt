package ru.tinkoff.kora.s3.client.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock

data class S3Operation(
    val method: KSFunctionDeclaration,
    val annotation: KSAnnotation,
    val code: CodeBlock
) {
    enum class OperationType {
        GET,
        LIST,
        PUT,
        DELETE
    }
}
