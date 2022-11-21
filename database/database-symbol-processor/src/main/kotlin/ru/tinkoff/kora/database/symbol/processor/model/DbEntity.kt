package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.database.symbol.processor.parseColumnName
import ru.tinkoff.kora.ksp.common.parseMappingData
import ru.tinkoff.kora.ksp.common.MappersData
import ru.tinkoff.kora.ksp.common.getNameConverter

data class DbEntity(val type: KSType, val classDeclaration: KSClassDeclaration, val fields: List<EntityField>) {
    data class EntityField(val type: KSType, val property: KSPropertyDeclaration, val columnName: String, val mapping: MappersData)

    companion object {
        @OptIn(KspExperimental::class)
        fun parseEntity(type: KSType): DbEntity? {
            if (type.declaration !is KSClassDeclaration) {
                return null
            }
            val typeDeclaration = type.declaration as KSClassDeclaration
            if (!typeDeclaration.modifiers.contains(Modifier.DATA)) {
                return null
            }
            val nameConverter = typeDeclaration.getNameConverter()
            val property = lambda@{ p: KSValueParameter ->
                for (property in typeDeclaration.getAllProperties()) {
                    if (property.simpleName.getShortName() == p.name!!.getShortName()) {
                        return@lambda property
                    }
                }
                throw IllegalStateException()
            }
            val fields = typeDeclaration.primaryConstructor!!.parameters
                .map { EntityField(it.type.resolve(), property(it), parseColumnName(it, nameConverter), it.parseMappingData()) }
                .toList()
            return DbEntity(
                typeDeclaration.asStarProjectedType(),
                typeDeclaration,
                fields
            )
        }

    }
}
