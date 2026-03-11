package io.koraframework.database.symbol.processor.cassandra.udt

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.koraframework.database.symbol.processor.cassandra.CassandraTypes.udt
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.visitClass

class CassandraUdtSymbolProcessor(val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private val resultExtractorGenerator = UserDefinedTypeResultExtractorGenerator(environment)
    private val statementSetterGenerator = UserDefinedTypeStatementSetterGenerator(environment)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(udt.canonicalName)
        val unprocessed = mutableListOf<KSAnnotated>()
        for (udtType in symbols) {
            if (!udtType.validateAll()) {
                unprocessed.add(udtType)
                continue
            }
            udtType.visitClass { this.processUdtClass(it) }
        }
        return unprocessed
    }

    private fun processUdtClass(classDeclaration: KSClassDeclaration) {
        resultExtractorGenerator.generate(classDeclaration)
        statementSetterGenerator.generate(classDeclaration)
    }

}
