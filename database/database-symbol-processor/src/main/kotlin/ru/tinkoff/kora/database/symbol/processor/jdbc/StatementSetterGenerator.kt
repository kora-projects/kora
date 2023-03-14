package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object StatementSetterGenerator {

    fun generate(b: FunSpec.Builder, function: KSFunctionDeclaration, queryWithParameters: QueryWithParameters, parameters: List<QueryParameter>, batchParam: QueryParameter?, parameterMappers: FieldFactory) {
        if (batchParam != null) {
            b.beginControlFlow("for (_batch_%L in %N)", batchParam.name, batchParam.name)
        }
        parameters.forEachIndexed { i, p ->
            var parameter = p
            if (parameter is QueryParameter.ConnectionParameter) {
                return@forEachIndexed
            }
            var parameterName = parameter.name
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
                parameterName = "_batch_${parameter.name}"
            }
            if (parameter is QueryParameter.SimpleParameter) {
                val sqlParameter = queryWithParameters.find(i)!!
                val mapping = parameter.variable.parseMappingData().getMapping(JdbcTypes.jdbcParameterColumnMapper)
                val nativeType = JdbcNativeTypes.findNativeType(parameter.type.toTypeName())
                if (nativeType != null && mapping == null) {
                    if (parameter.type.isMarkedNullable) {
                        b.controlFlow("%L.let", parameterName) {
                            b.controlFlow("if (it == null)") {
                                for (idx in sqlParameter.sqlIndexes) {
                                    b.addCode(nativeType.bindNull("_stmt", idx + 1)).addCode("\n")
                                }
                                b.nextControlFlow("else")
                                for (idx in sqlParameter.sqlIndexes) {
                                    b.addCode(nativeType.bind("_stmt", "it", idx + 1)).addCode("\n")
                                }
                            }
                        }
                    } else {
                        for (idx in sqlParameter.sqlIndexes) {
                            b.addCode(nativeType.bind("_stmt", parameterName, idx + 1)).addCode("\n")
                        }
                    }
                } else if (mapping?.mapper != null) {
                    for (idx in sqlParameter.sqlIndexes) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tags)
                        b.addStatement("%N.set(_stmt, %L, %N)", mapperName, idx + 1, parameterName)
                    }
                } else {
                    for (idx in sqlParameter.sqlIndexes) {
                        val mapperName = parameterMappers.get(JdbcTypes.jdbcParameterColumnMapper, parameter.type, parameter.variable)
                        b.addStatement("%N.set(_stmt, %L, %N)", mapperName, idx + 1, parameterName)
                    }
                }
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.columns) {
                    val fieldPropertyName = field.property.simpleName.getShortName()
                    val fieldName = "$parameterName?.$fieldPropertyName"
                    val sqlParameter = queryWithParameters.find(field.queryParameterName(parameter.name))
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        continue
                    }
                    val nativeType = JdbcNativeTypes.findNativeType(field.type.toTypeName())
                    val mapping = field.mapping.getMapping(JdbcTypes.jdbcParameterColumnMapper)
                    if (nativeType != null && mapping == null) {
                        if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                            b.controlFlow("%N?.%L.let", parameterName, field.accessor(true)) {
                                b.controlFlow("if (it == null)") {
                                    for (idx in sqlParameter.sqlIndexes) {
                                        b.addCode(nativeType.bindNull("_stmt", idx + 1)).addCode("\n")
                                    }
                                    b.nextControlFlow("else")
                                    for (idx in sqlParameter.sqlIndexes) {
                                        b.addCode(nativeType.bind("_stmt", "it", idx + 1)).addCode("\n")
                                    }
                                }
                            }
                        } else {
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addCode(nativeType.bind("_stmt", "$parameterName.${field.accessor(false)}", idx + 1)).addCode("\n")
                            }
                        }
                    } else if (mapping?.mapper != null) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tags)
                        for (idx in sqlParameter.sqlIndexes) {
                            b.addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, fieldName)
                        }
                    } else {
                        val mapperName = parameterMappers.get(JdbcTypes.jdbcParameterColumnMapper, field.type, field.property)
                        for (idx in sqlParameter.sqlIndexes) {
                            b.addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, fieldName)
                        }
                    }
                }
            }
        }
        if (batchParam != null) {
            b.addStatement("_stmt.addBatch()")
            b.endControlFlow()
        }
    }
}
