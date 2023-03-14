package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object R2dbcStatementSetterGenerator {
    fun generate(
        b: FunSpec.Builder,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        batchParam: QueryParameter?,
        parameterMappers: FieldFactory
    ) {
        if (batchParam != null) {
            b.beginControlFlow("for (_batch_%L in %N)", batchParam.name, batchParam.name)
        }
        var sqlIndex = 0
        parameters.forEach { p ->
            var parameter = p
            if (parameter is QueryParameter.ConnectionParameter) {
                return@forEach
            }
            var parameterName = parameter.name
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
                parameterName = "_batch_${parameterName}"
            }
            if (parameter is QueryParameter.SimpleParameter) {
                val nativeType = R2dbcNativeTypes.findNativeType(parameter.type.toTypeName())
                val mapping = parameter.variable.parseMappingData().getMapping(R2dbcTypes.parameterColumnMapper)
                if (nativeType != null && mapping == null) {
                    if (parameter.type.isMarkedNullable) {
                        b.controlFlow("if (%L != null)", parameterName) {
                            addCode(nativeType.bind("_stmt", parameterName, sqlIndex)).addCode("\n")
                            nextControlFlow("else")
                            addCode(nativeType.bindNull("_stmt", sqlIndex)).addCode("\n")
                        }
                    } else {
                        b.addCode(nativeType.bind("_stmt", parameterName, sqlIndex)).addCode("\n")
                    }
                } else if (mapping?.mapper != null) {
                    val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                    b.addCode("%N.apply(_stmt, %L, %N)\n", mapper, sqlIndex, parameterName)
                } else {
                    val mapper = parameterMappers.get(R2dbcTypes.parameterColumnMapper, parameter.type, parameter.variable)
                    b.addCode("%N.apply(_stmt, %L, %N)\n", mapper, sqlIndex, parameterName)
                }
                sqlIndex++
                return@forEach
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.columns) {
                    val sqlParam = query.find(field.queryParameterName(parameter.name))
                    if (sqlParam?.sqlIndexes.isNullOrEmpty()) {
                        continue
                    }
                    val nativeType = R2dbcNativeTypes.findNativeType(field.type.toTypeName())
                    val accessor = if (parameter.type.isMarkedNullable || field.isNullable) {
                        parameterName + "?." + field.accessor(true)
                    } else {
                        parameterName + "." + field.accessor(false)
                    }
                    val mapping = field.mapping.getMapping(R2dbcTypes.parameterColumnMapper)
                    if (nativeType != null && mapping == null) {
                        if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                            b.beginControlFlow("if (%L != null)", accessor)
                        }
                        b.addCode(nativeType.bind("_stmt", accessor, sqlIndex)).addCode("\n")
                        if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                            b.nextControlFlow("else")
                            b.addCode(nativeType.bindNull("_stmt", sqlIndex)).addCode("\n")
                            b.endControlFlow()
                        }
                    } else if (mapping?.mapper != null) {
                        val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                        b.addStatement("%N.apply(_stmt, %L, %L)", mapper, sqlIndex, accessor)
                    } else {
                        val mapper = parameterMappers.get(R2dbcTypes.parameterColumnMapper, field.type, field.property)
                        b.addStatement("%N.apply(_stmt, %L, %L)", mapper, sqlIndex, accessor)
                    }
                    sqlIndex++
                }
            }
        }
        if (batchParam != null) {
            b.endControlFlow()
        }
    }
}
