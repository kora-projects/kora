package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor

class CassandraEntitySymbolProcessor(environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private val generator = CassandraEntityGenerator(environment.codeGenerator)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        for (annotated in resolver.getSymbolsWithAnnotation(CassandraTypes.entity.canonicalName)) {
            if (annotated !is KSClassDeclaration || !annotated.modifiers.contains(Modifier.DATA)) {
                kspLogger.error("@EntityCassandra only works on records and java bean like classes", annotated)
                continue
            }
            val entity = DbEntity.parseEntity(annotated.asStarProjectedType())
            if (entity == null) {
                kspLogger.error("Can't parse entity from type: $annotated", annotated)
                continue
            }
            generator.generateRowMapper(entity)
            generator.generateResultSetMapper(entity)
            generator.generateListResultSetMapper(entity)
        }
        return emptyList()
    }
}
