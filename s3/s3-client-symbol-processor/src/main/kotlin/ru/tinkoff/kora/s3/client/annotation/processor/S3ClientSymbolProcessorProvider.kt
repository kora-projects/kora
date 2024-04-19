package ru.tinkoff.kora.s3.client.annotation.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class S3ClientSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) = S3ClientSymbolProcessor(environment)
}
