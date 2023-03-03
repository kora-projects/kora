package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object StatementSetterGenerator {
    fun generate(
        b: FunSpec.Builder,
        queryWithParameters: QueryWithParameters,
        parameters: List<QueryParameter>,
        batchParam: QueryParameter?,
        parameterMappers: FieldFactory
    ) {
        if (batchParam != null) {
            b.addStatement("val _batch = %T.builder(%T.UNLOGGED)", CassandraTypes.batchStatement, CassandraTypes.defaultBatchType)
            b.beginControlFlow("for (_i in 0 until %N.size)", batchParam.name)
            b.addCode("val _param_%L = %N[_i]\n", batchParam.name, batchParam.name)
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
                    b.beginControlFlow("if (%N == null)", parameterName)
                    for (idx in sqlParameter.sqlIndexes) {
                        b.addStatement("_stmt.setToNull(%L)", idx)
                    }
                    b.nextControlFlow("else")
                }
                val nativeType = CassandraNativeTypes.findNativeType(parameter.type.toTypeName())
                val mapping = parameter.variable.parseMappingData().getMapping(CassandraTypes.parameterColumnMapper)
                if (nativeType != null && mapping == null) {
                    for (idx in sqlParameter.sqlIndexes) {
                        b.addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("%N", parameterName), CodeBlock.of("%L", idx)));
                    }
                } else if (mapping?.mapper != null) {
                    val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                    for (idx in sqlParameter.sqlIndexes) {
                        b.addStatement("%N.apply(_stmt, %L, %N)", mapper, idx, parameter.variable.name!!.asString())
                    }
                } else {
                    val mapper = parameterMappers.get(CassandraTypes.parameterColumnMapper, parameter.type, parameter.variable)
                    for (idx in sqlParameter.sqlIndexes) {
                        b.addStatement("%N.apply(_stmt, %L, %N)", mapper, idx, parameter.variable.name!!.asString())
                    }
                }
                if (isNullable) {
                    b.endControlFlow();
                }
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.fields) {
                    val parameterNullable = parameter.type.isMarkedNullable
                    val fieldNullable = field.type.isMarkedNullable
                    val sqlParameter = queryWithParameters.find("${parameter.name}.${field.property.simpleName.getShortName()}")
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        continue
                    }

                    b.addCode("%N", parameterName)
                    if (parameterNullable) b.addCode("?")
                    b.controlFlow(".%N.let {", field.property.simpleName.asString()) {
                        if (parameterNullable || fieldNullable) {
                            b.beginControlFlow("if (it == null)")
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addStatement("_stmt.setToNull(%L)", idx)
                            }
                            b.nextControlFlow("else")
                        }
                        val nativeType = CassandraNativeTypes.findNativeType(field.type.toTypeName());
                        val mapping = field.mapping.getMapping(CassandraTypes.parameterColumnMapper)
                        if (nativeType != null && mapping == null) {
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("it"), CodeBlock.of("%L", idx)))
                            }
                        } else if (mapping?.mapper != null) {
                            val mapper = parameterMappers.get(mapping.mapper!!, mapping.tags)
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addStatement("%N.apply(_stmt, %L, it);\n", mapper, idx)
                            }
                        } else {
                            val mapper = parameterMappers.get(CassandraTypes.parameterColumnMapper, field.type, field.property);
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addStatement("%N.apply(_stmt, %L, it);\n", mapper, idx)
                            }
                        }
                        if (parameterNullable || fieldNullable) {
                            b.endControlFlow()
                        }
                    }

                }
            }
        }
        if (batchParam != null) {
            b.addStatement("val _builtStmt = _stmt.build()")
            b.addStatement("_batch.addStatement(_builtStmt)")
            b.addCode("_stmt = %T(_builtStmt)", ClassName("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder"))
            b.endControlFlow()
            b.addStatement("val _s = _batch.build()")
        } else {
            b.addStatement("val _s = _stmt.build()")
        }
    }
}
