package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object StatementSetterGenerator {
    fun CodeBlock.Builder.setPreparedStatementParams(
        queryWithParameters: QueryWithParameters,
        parameters: List<QueryParameter>,
        batchParam: QueryParameter?,
        parameterMappers: FieldFactory
    ) {
        if (batchParam != null) {
            addStatement("val _batch = %T.builder(%T.UNLOGGED)", CassandraTypes.batchStatement, CassandraTypes.defaultBatchType)
            beginControlFlow("for (_i in 0 until %N.size)", batchParam.name)
            add("val _param_%L = %N[_i]\n", batchParam.name, batchParam.name)
        }
        for (i in parameters.indices) {
            var parameter = parameters[i]
            if (parameter is QueryParameter.ConnectionParameter) {
                continue
            }
            var parameterName = parameter.name
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
                parameterName = "_param_${parameter.name}"
            }
            if (parameter is QueryParameter.SimpleParameter) {
                val sqlParameter = queryWithParameters.find(i)!!
                val isNullable = parameter.type.isMarkedNullable
                if (isNullable) {
                    beginControlFlow("if (%N == null)", parameterName)
                    for (idx in sqlParameter.sqlIndexes) {
                        addStatement("_stmt.setToNull(%L)", idx)
                    }
                    nextControlFlow("else")
                }
                val nativeType = CassandraNativeTypes.findNativeType(parameter.type.toTypeName())
                val mapping = parameter.variable.parseMappingData().getMapping(CassandraTypes.parameterColumnMapper)
                if (nativeType != null && mapping == null) {
                    for (idx in sqlParameter.sqlIndexes) {
                        addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("%N", parameterName), CodeBlock.of("%L", idx)));
                    }
                } else if (mapping?.mapper != null) {
                    val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                    for (idx in sqlParameter.sqlIndexes) {
                        addStatement("%N.apply(_stmt, %L, %N)", mapper, idx, parameter.variable.name!!.asString())
                    }
                } else {
                    val mapper = parameterMappers.get(CassandraTypes.parameterColumnMapper, parameter.type, parameter.variable)
                    for (idx in sqlParameter.sqlIndexes) {
                        addStatement("%N.apply(_stmt, %L, %N)", mapper, idx, parameter.variable.name!!.asString())
                    }
                }
                if (isNullable) {
                    endControlFlow();
                }
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.columns) {
                    val parameterNullable = parameter.type.isMarkedNullable
                    val fieldNullable = field.isNullable
                    val sqlParameter = queryWithParameters.find(field.queryParameterName(parameter.name))
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        continue
                    }

                    add("%N", parameterName)
                    if (parameterNullable) add("?")
                    controlFlow(".%L.let {", field.accessor(parameterNullable || fieldNullable)) {
                        if (parameterNullable || fieldNullable) {
                            beginControlFlow("if (it == null)")
                            for (idx in sqlParameter.sqlIndexes) {
                                addStatement("_stmt.setToNull(%L)", idx)
                            }
                            nextControlFlow("else")
                        }
                        val nativeType = CassandraNativeTypes.findNativeType(field.type.toTypeName());
                        val mapping = field.mapping.getMapping(CassandraTypes.parameterColumnMapper)
                        if (nativeType != null && mapping == null) {
                            for (idx in sqlParameter.sqlIndexes) {
                                addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("it"), CodeBlock.of("%L", idx)))
                            }
                        } else if (mapping?.mapper != null) {
                            val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                            for (idx in sqlParameter.sqlIndexes) {
                                addStatement("%N.apply(_stmt, %L, it)", mapper, idx)
                            }
                        } else {
                            val mapper = parameterMappers.get(CassandraTypes.parameterColumnMapper, field.type, field.property);
                            for (idx in sqlParameter.sqlIndexes) {
                                addStatement("%N.apply(_stmt, %L, it)", mapper, idx)
                            }
                        }
                        if (parameterNullable || fieldNullable) {
                            endControlFlow()
                        }
                    }
                }

                val parameterNullable = parameter.type.isMarkedNullable
                val sqlParameter = queryWithParameters.find(parameter.name)
                if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                    continue
                }

                add("%N", parameterName)
                if (parameterNullable) {
                    add("?")
                }
                controlFlow(".let {") {
                    if (parameterNullable) {
                        beginControlFlow("if (it == null)")
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("_stmt.setToNull(%L)", idx)
                        }
                        nextControlFlow("else")
                    }
                    val mappersData = parameter.entity.classDeclaration.parseMappingData()
                    val mapping = mappersData.getMapping(parameter.entity.type)
                    if (mapping?.mapper != null) {
                        val mapper = parameterMappers[mapping.mapper!!, mappersData.tags]
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.apply(_stmt, %L, it)", mapper, idx)
                        }
                    } else {
                        val mapper = parameterMappers[CassandraTypes.parameterColumnMapper, parameter.entity.type, parameter.entity.classDeclaration];
                        for (idx in sqlParameter.sqlIndexes) {
                            addStatement("%N.apply(_stmt, %L, it)", mapper, idx)
                        }
                    }
                    if (parameterNullable) {
                        endControlFlow()
                    }
                }
            }
        }

        if (batchParam != null) {
            addStatement("val _builtStmt = _stmt.build()")
            addStatement("_batch.addStatement(_builtStmt)")
            add("_stmt = %T(_builtStmt)", ClassName("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder"))
            endControlFlow()
            addStatement("val _s = _batch.build()")
        } else {
            addStatement("val _s = _stmt.build()")
        }
    }
}
