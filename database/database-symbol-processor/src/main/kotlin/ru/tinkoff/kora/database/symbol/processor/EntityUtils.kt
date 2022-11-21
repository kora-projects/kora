package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseAnnotationValue

private val defaultColumnNameConverter = SnakeCaseNameConverter()

fun parseColumnName(valueParameter: KSValueParameter, columnsNameConverter: NameConverter?): String {
    val column = valueParameter.findAnnotation(DbUtils.columnAnnotation)
    if (column != null) {
        return parseAnnotationValue<String>(column, "value")!!
    }
    val fieldName = valueParameter.name!!.asString()
    if (columnsNameConverter != null) {
        return columnsNameConverter.convert(fieldName)
    }
    return defaultColumnNameConverter.convert(fieldName)
}

fun parseColumnName(valueParameter: KSPropertyDeclaration, columnsNameConverter: NameConverter?): String {
    val column = valueParameter.findAnnotation(DbUtils.columnAnnotation)
    if (column != null) {
        return parseAnnotationValue<String>(column, "value")!!
    }
    val fieldName = valueParameter.simpleName.asString()
    if (columnsNameConverter != null) {
        return columnsNameConverter.convert(fieldName)
    }
    return defaultColumnNameConverter.convert(fieldName)
}


fun findEntityConstructor(declaration: KSClassDeclaration): KSFunctionDeclaration {
    val constructors = declaration.getConstructors()
        .filter { it.isPublic() }
        .toList()
    if (constructors.isEmpty()) {
        throw ProcessingErrorException(
            listOf(
                ProcessingError(
                    "Entity type ${declaration.simpleName.asString()} has no public constructors", declaration
                )
            )
        )
    }
    if (constructors.size == 1) {
        return constructors[0]
    }
    val entityConstructors = constructors
        .filter { c -> c.findAnnotation(DbUtils.entityConstructorAnnotation) != null }
        .toList()
    if (entityConstructors.isEmpty()) {
        throw ProcessingErrorException(
            listOf(
                ProcessingError(
                    "Entity type ${declaration.simpleName.asString()} has more than one public constructor and none of them is marked with @EntityConstructor", declaration
                )
            )
        )
    }
    if (entityConstructors.size != 1) {
        throw ProcessingErrorException(
            listOf(
                ProcessingError(
                    "Entity type ${declaration.simpleName.asString()} has more than one public constructor and more then one of them is marked with @EntityConstructor", declaration
                )
            )
        )
    }
    return entityConstructors[0]
}
