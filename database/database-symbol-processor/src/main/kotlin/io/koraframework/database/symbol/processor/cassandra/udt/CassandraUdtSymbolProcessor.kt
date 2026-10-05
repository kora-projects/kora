package io.koraframework.database.symbol.processor.cassandra.udt

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.generatedClassName
import io.koraframework.ksp.common.generatedHolder
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

    /**
     * All mappers of the type are written as nested classes of a single holder, e.g. `$Udt_CassandraUdt.RowColumnMapper`
     */
    private fun processUdtClass(classDeclaration: KSClassDeclaration) {
        val holder = generatedHolder(classDeclaration.generatedClassName(HOLDER_POSTFIX), CassandraUdtSymbolProcessor::class)
            .addTypes(statementSetterGenerator.generate(classDeclaration))
            .addTypes(resultExtractorGenerator.generate(classDeclaration))
            .build()

        FileSpec.get(classDeclaration.packageName.asString(), holder).writeTo(environment.codeGenerator, false, listOfNotNull(classDeclaration.containingFile))
    }

    companion object {
        const val HOLDER_POSTFIX = "CassandraUdt"
        const val PARAMETER_COLUMN_MAPPER_NAME = "ParameterColumnMapper"
        const val LIST_PARAMETER_COLUMN_MAPPER_NAME = "ListParameterColumnMapper"
        const val ROW_COLUMN_MAPPER_NAME = "RowColumnMapper"
        const val LIST_ROW_COLUMN_MAPPER_NAME = "ListRowColumnMapper"
    }

}
