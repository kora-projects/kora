package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.KspCommonUtils
import ru.tinkoff.kora.ksp.common.parseAnnotationValue


val snakeCaseNameConverter = KspCommonUtils.NameConverter { originalName ->
    originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)")
        .map { it.lowercase() }
        .joinToString("_")
}

fun parseColumnName(valueParameter: KSValueParameter, propertyParameter: KSPropertyDeclaration, columnsNameConverter: KspCommonUtils.NameConverter?): String {
    val column = valueParameter.findAnnotation(DbUtils.columnAnnotation)
        ?: propertyParameter.findAnnotation(DbUtils.columnAnnotation)
    if (column != null) {
        return parseAnnotationValue<String>(column, "value")!!
    }

    val fieldName = valueParameter.name!!.asString()
    if (columnsNameConverter != null) {
        return columnsNameConverter.convert(fieldName)
    }

    return snakeCaseNameConverter.convert(fieldName)
}
