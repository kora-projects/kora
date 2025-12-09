package ru.tinkoff.kora.aws.s3.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class S3ClientSymbolProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = S3ClientSymbolProcessor(environment)
}
