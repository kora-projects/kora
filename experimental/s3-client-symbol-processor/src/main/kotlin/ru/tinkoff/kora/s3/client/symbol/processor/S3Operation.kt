package ru.tinkoff.kora.s3.client.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock

data class S3Operation(
    val method: KSFunctionDeclaration,
    val annotation: KSAnnotation,
    val type: OperationType,
    val impl: ImplType,
    val mode: Mode,
    val code: CodeBlock
) {
    enum class Mode {
        ASYNC,
        SYNC
    }

    enum class OperationType {
        GET,
        LIST,
        PUT,
        DELETE
    }

    enum class ImplType {
        SIMPLE,
        AWS,
        MINIO
    }
}
