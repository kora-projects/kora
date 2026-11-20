package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Generates a source file in the first round, like another processor whose type a Mongo entity or repository refers to.
 */
class LaterRoundProcessorProvider(
    private val packageName: String,
    private val fileName: String,
    private val body: String
) : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = object : SymbolProcessor {
        private var done = false

        override fun process(resolver: Resolver): List<KSAnnotated> {
            if (!done) {
                done = true
                environment.codeGenerator.createNewFile(Dependencies(false), packageName, fileName).writer().use {
                    it.write("package $packageName\n$body\n")
                }
            }
            return emptyList()
        }
    }
}
