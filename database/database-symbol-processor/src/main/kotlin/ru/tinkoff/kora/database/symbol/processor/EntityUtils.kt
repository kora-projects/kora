package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
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
