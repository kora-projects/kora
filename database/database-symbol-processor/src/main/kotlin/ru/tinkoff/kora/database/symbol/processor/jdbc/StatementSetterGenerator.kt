package ru.tinkoff.kora.database.symbol.processor.jdbc

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object StatementSetterGenerator {

    fun CodeBlock.Builder.setStatementParams(queryWithParameters: QueryWithParameters, parameters: List<QueryParameter>, batchParam: QueryParameter?, parameterMappers: FieldFactory) {
        if (batchParam != null) {
            beginControlFlow("for (_batch_%L in %N)", batchParam.name, batchParam.name)
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
                        controlFlow("%L.let", parameterName) {
                            controlFlow("if (it == null)") {
                                for (idx in sqlParameter.sqlIndexes) {
                                    add(nativeType.bindNull("_stmt", idx + 1)).add("\n")
                                }
                                nextControlFlow("else")
                                for (idx in sqlParameter.sqlIndexes) {
                                    add(nativeType.bind("_stmt", "it", idx + 1)).add("\n")
                                }
                            }
                        }
                    } else {
                        for (idx in sqlParameter.sqlIndexes) {
                            add(nativeType.bind("_stmt", parameterName, idx + 1)).add("\n")
                        }
                    }
                } else if (mapping?.mapper != null) {
                    for (idx in sqlParameter.sqlIndexes) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tag)
                        addStatement("%N.set(_stmt, %L, %N)", mapperName, idx + 1, parameterName)
                    }
                } else {
                    for (idx in sqlParameter.sqlIndexes) {
                        val mapperName = parameterMappers.get(JdbcTypes.jdbcParameterColumnMapper, parameter.type, parameter.variable)
                        addStatement("%N.set(_stmt, %L, %N)", mapperName, idx + 1, parameterName)
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
                            controlFlow("%N?.%L.let", parameterName, field.accessor(true)) {
                                controlFlow("if (it == null)") {
                                    for (idx in sqlParameter.sqlIndexes) {
                                        add(nativeType.bindNull("_stmt", idx + 1)).add("\n")
                                    }
                                    nextControlFlow("else")
                                    for (idx in sqlParameter.sqlIndexes) {
                                        add(nativeType.bind("_stmt", "it", idx + 1)).add("\n")
                                    }
                                }
                            }
                        } else {
                            for (idx in sqlParameter.sqlIndexes) {
                                add(nativeType.bind("_stmt", "$parameterName.${field.accessor(field.isNullable)}", idx + 1)).add("\n")
                            }
                        }
                    } else if (mapping?.mapper != null) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tag)
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, fieldName)
                        }
                    } else {
                        val mapperName = parameterMappers.get(JdbcTypes.jdbcParameterColumnMapper, field.type, field.property)
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, "$parameterName.${field.accessor(field.isNullable)}")
                        }
                    }
                }

                val sqlParameter = queryWithParameters.find(parameter.name)
                if (sqlParameter != null && sqlParameter.sqlIndexes.isNotEmpty()) {
                    val mappersData = parameter.entity.classDeclaration.parseMappingData()
                    val mapping = mappersData.getMapping(parameter.entity.type)
                    if (mapping?.mapper != null) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tag)
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, parameter.name)
                        }
                    } else {
                        val mapperName = parameterMappers.get(JdbcTypes.jdbcParameterColumnMapper, parameter.entity.type, parameter.entity.classDeclaration)
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.set(_stmt, %L, %L)", mapperName, idx + 1, parameter.name)
                        }
                    }
                }
            }
        }

        if (batchParam != null) {
            addStatement("_stmt.addBatch()")
            endControlFlow()
        }
    }
}
