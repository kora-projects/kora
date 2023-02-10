package ru.tinkoff.kora.database.symbol.processor.cassandra.udt

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes.udt
import ru.tinkoff.kora.ksp.common.visitClass

class CassandraUdtSymbolProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val resultExtractorGenerator = UserDefinedTypeResultExtractorGenerator(environment)
    private val statementSetterGenerator = UserDefinedTypeStatementSetterGenerator(environment)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(udt.canonicalName)
        val unprocessed = mutableListOf<KSAnnotated>()
        for (udtType in symbols) {
            if (!udtType.validate()) {
                unprocessed.add(udtType)
                continue
            }
            udtType.visitClass { this.processUdtClass(resolver, it) }
        }
        return unprocessed
    }

    private fun processUdtClass(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        resultExtractorGenerator.generate(resolver, classDeclaration)
        statementSetterGenerator.generate(resolver, classDeclaration)
    }

}
