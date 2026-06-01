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
            b.addStatement("var counter = 0")
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
                var sqlParameter = query.find(parameter.name)
                if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                    return@forEach
                }

                val nativeType = R2dbcNativeTypes.findNativeType(parameter.type.toTypeName())
                val mapping = parameter.variable.parseMappingData().getMapping(R2dbcTypes.parameterColumnMapper)
                if (nativeType != null && mapping == null) {
                    sqlParameter.sqlIndexes.forEach { index ->
                        if (parameter.type.isMarkedNullable) {
                            b.controlFlow("if (%L != null)", parameterName) {
                                addCode(nativeType.bind("_stmt", parameterName, index)).addCode("\n")
                                nextControlFlow("else")
                                addCode(nativeType.bindNull("_stmt", index)).addCode("\n")
                            }
                        } else {
                            b.addCode(nativeType.bind("_stmt", parameterName, index)).addCode("\n")
                        }
                    }
                } else if (mapping?.mapper != null) {
                    sqlParameter.sqlIndexes.forEach { index ->
                        val mapper = parameterMappers[mapping.mapper!!, mapping.tags]
                        b.addCode("%N.apply(_stmt, %L, %N)\n", mapper, index, parameterName)
                    }
                } else {
                    sqlParameter.sqlIndexes.forEach { index ->
                        val mapper = parameterMappers[R2dbcTypes.parameterColumnMapper, parameter.type, parameter.variable]
                        b.addCode("%N.apply(_stmt, %L, %N)\n", mapper, index, parameterName)
                    }
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

                    var sqlParameter = query.find(field.queryParameterName(parameter.name))
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        return@forEach
                    }

                    val nativeType = R2dbcNativeTypes.findNativeType(field.type.toTypeName())
                    val accessor = if (parameter.type.isMarkedNullable || field.isNullable) {
                        parameterName + "?." + field.accessor(true)
                    } else {
                        parameterName + "." + field.accessor(false)
                    }
                    val mapping = field.mapping.getMapping(R2dbcTypes.parameterColumnMapper)
                    if (nativeType != null && mapping == null) {
                        sqlParameter.sqlIndexes.forEach { index ->
                            if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                                b.beginControlFlow("if (%L != null)", accessor)
                            }
                            b.addCode(nativeType.bind("_stmt", accessor, index)).addCode("\n")
                            if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                                b.nextControlFlow("else")
                                b.addCode(nativeType.bindNull("_stmt", index)).addCode("\n")
                                b.endControlFlow()
                            }
                        }
                    } else if (mapping?.mapper != null) {
                        sqlParameter.sqlIndexes.forEach { index ->
                            val mapper = parameterMappers[mapping.mapper!!, mapping.tags]
                            b.addStatement("%N.apply(_stmt, %L, %L)", mapper, index, accessor)
                        }
                    } else {
                        sqlParameter.sqlIndexes.forEach { index ->
                            val mapper = parameterMappers[R2dbcTypes.parameterColumnMapper, field.type, field.property]
                            b.addStatement("%N.apply(_stmt, %L, %L)", mapper, index, accessor)
                        }
                    }
                    sqlIndex++
                }

                val sqlParam = query.find(parameter.name)
                if (!sqlParam?.sqlIndexes.isNullOrEmpty()) {
                    val accessor = parameterName
                    val mappersData = parameter.entity.classDeclaration.parseMappingData()
                    val mapping = mappersData.getMapping(parameter.entity.type)
                    if (mapping?.mapper != null) {
                        sqlParam?.sqlIndexes?.forEach { index ->
                            val mapper = parameterMappers[mapping.mapper!!, mapping.tags]
                            b.addStatement("%N.apply(_stmt, %L, %L)", mapper, index, accessor)
                        }
                    } else {
                        sqlParam?.sqlIndexes?.forEach { index ->
                            val mapper = parameterMappers[R2dbcTypes.parameterColumnMapper, parameter.entity.type, parameter.entity.classDeclaration]
                            b.addStatement("%N.apply(_stmt, %L, %L)", mapper, index, accessor)
                        }
                    }
                    sqlIndex++
                }
            }
        }

        if (batchParam != null) {
            b.controlFlow("if (counter != %L.size - 1)", batchParam.name) {
                addStatement("_stmt.add()")
            }
            b.addStatement("counter++")
            b.endControlFlow()
        }
    }
}
