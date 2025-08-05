package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor

class JdbcEntitySymbolProcessor(environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private val generator = JdbcEntityGenerator(environment.codeGenerator)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        for (annotated in resolver.getSymbolsWithAnnotation(JdbcTypes.jdbcEntity.canonicalName)) {
            if (annotated !is KSClassDeclaration || !annotated.modifiers.contains(Modifier.DATA)) {
                kspLogger.error("@EntityJdbc only works on data classes, records and java bean like classes", annotated)
                continue
            }
            val entity = DbEntity.parseEntity(annotated.asStarProjectedType())
            if (entity == null) {
                kspLogger.error("Can't parse entity from type: $annotated", annotated)
                continue
            }
            generator.generateRowMapper(entity, false)
            generator.generateListResultSetMapper(entity, false)
            generator.generateResultSetMapper(entity, false)
        }
        return emptyList()
    }
}
