package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils.parameterMapperName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow

object StatementSetterGenerator {
    fun generate(
        b: FunSpec.Builder,
        method: KSFunctionDeclaration,
        queryWithParameters: QueryWithParameters,
        parameters: List<QueryParameter>,
        batchParam: QueryParameter?
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
            if (parameter is QueryParameter.ParameterWithMapper) {
                val mapperName = parameterMapperName(method, parameter.variable)
                b.addCode(
                    "this.%N.apply(_stmt, %N)\n",
                    mapperName,
                    parameterName
                )
                continue
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
                val nativeType = CassandraNativeTypes.findNativeType(parameter.type.toTypeName());
                if (nativeType != null) {
                    for (idx in sqlParameter.sqlIndexes) {
                        b.addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("%N", parameterName), CodeBlock.of("%L", idx)));
                    }
                } else {
                    for (idx in sqlParameter.sqlIndexes) {
                        val mapper = parameterMapperName(method, parameter.variable)
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
                    val sqlParameter = queryWithParameters.find("$parameterName.${field.property.simpleName.getShortName()}")
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
                        if (nativeType != null) {
                            for (idx in sqlParameter.sqlIndexes) {
                                b.addStatement("%L", nativeType.bind("_stmt", CodeBlock.of("it"), CodeBlock.of("%L", idx)))
                            }
                        } else {
                            for (idx in sqlParameter.sqlIndexes) {
                                val mapper = parameterMapperName(method, parameter.variable, field.property.simpleName.getShortName());
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
