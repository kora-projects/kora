package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

object R2dbcStatementSetterGenerator {
    fun generate(
        b: FunSpec.Builder,
        function: KSFunctionDeclaration,
        query: QueryWithParameters,
        parameters: List<QueryParameter>,
        batchParam: QueryParameter?
    ) {
        if (batchParam != null) {
            b.beginControlFlow("for (_batch_%L in %N)", batchParam.name, batchParam.name)
        }
        var sqlIndex = 0
        parameters.forEach {  p ->
            var parameter = p
            if (parameter is QueryParameter.ConnectionParameter) {
                return@forEach
            }
            var parameterName = parameter.name
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
                parameterName = "_batch_${parameterName}"
            }
            if (parameter is QueryParameter.ParameterWithMapper) {
                val mapper = DbUtils.parameterMapperName(function, parameter.variable)
                b.addStatement("%N.apply(_stmt, %L, %N)", mapper, sqlIndex, parameterName)
                sqlIndex++
                return@forEach
            }
            if (parameter is QueryParameter.SimpleParameter) {
                val nativeType = R2dbcNativeTypes.findNativeType(parameter.type.toTypeName())
                val mapper = parameter.variable.parseMappingData().getMapping(R2dbcTypes.parameterColumnMapper)
                if (nativeType != null && mapper == null) {
                    if (parameter.type.isMarkedNullable) {
                        b.controlFlow("if (%L != null)", parameterName) {
                            addCode(nativeType.bind("_stmt", parameterName, sqlIndex)).addCode("\n")
                            nextControlFlow("else")
                            addCode(nativeType.bindNull("_stmt", sqlIndex)).addCode("\n")
                        }
                    } else {
                        b.addCode(nativeType.bind("_stmt", parameterName, sqlIndex)).addCode("\n")
                    }
                } else {
                    b.addCode("%N.apply(_stmt, %L, %N)\n", DbUtils.parameterMapperName(function, parameter.variable), sqlIndex, parameterName)
                }
                sqlIndex++
                return@forEach
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.fields) {
                    val sqlParam = query.find(parameter.name + "." + field.property.simpleName.getShortName())
                    if (sqlParam?.sqlIndexes.isNullOrEmpty()) {
                        continue
                    }
                    val nativeType = R2dbcNativeTypes.findNativeType(field.type.toTypeName())
                    val fieldValue = if (parameter.type.isMarkedNullable) {
                        parameterName + "?." + field.property.simpleName.getShortName()
                    } else {
                        parameterName + "." + field.property.simpleName.getShortName()
                    }
                    val mapper = field.mapping.getMapping(R2dbcTypes.parameterColumnMapper)
                    if (nativeType != null && mapper == null) {
                        if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                            b.beginControlFlow("if (%L != null)", fieldValue)
                        }
                        b.addCode(nativeType.bind("_stmt", fieldValue, sqlIndex)).addCode("\n")
                        if (parameter.type.isMarkedNullable || field.type.isMarkedNullable) {
                            b.nextControlFlow("else")
                            b.addCode(nativeType.bindNull("_stmt", sqlIndex)).addCode("\n")
                            b.endControlFlow()
                        }
                    } else {
                        val mapper = DbUtils.parameterMapperName(function, parameter.variable, field.property.simpleName.getShortName())
                        b.addStatement("%N.apply(_stmt, %L, %L)", mapper, sqlIndex, fieldValue)
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
