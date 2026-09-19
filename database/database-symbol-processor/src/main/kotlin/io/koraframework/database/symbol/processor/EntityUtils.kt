package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.KspCommonUtils
import io.koraframework.ksp.common.parseAnnotationValue


private val SNAKE_CASE_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)")

val snakeCaseNameConverter = KspCommonUtils.NameConverter { originalName ->
    originalName.split(SNAKE_CASE_BOUNDARY)
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
