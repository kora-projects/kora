package ru.tinkoff.kora.database.symbol.processor.vertx

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils.parameterMapperName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter

object ParametersToTupleBuilder {
    fun generate(b: FunSpec.Builder, query: QueryWithParameters, method: KSFunctionDeclaration, parameters: List<QueryParameter>, batchParam: QueryParameter?) {
        if (batchParam != null) {
            b.addCode(
                "val _batchParams = %T<%T>(%L.size)\n",
                ArrayList::class,
                VertxTypes.tuple,
                batchParam.variable.name!!.asString()
            )
            b.beginControlFlow("for (_batch_%L in %L)", batchParam.name, batchParam.name)
        }
        data class Param(val indexes: List<Int>, val name: String, val code: CodeBlock)

        val sqlParams = parameters.asSequence()
            .filter { it !is QueryParameter.ConnectionParameter }
            .map {
                if (it is QueryParameter.BatchParameter) {
                    "_batch_" + it.name to it.parameter
                } else {
                    it.name to it
                }
            }
            .flatMap {
                val param = it.second
                if (param is QueryParameter.SimpleParameter) {
                    val nativeType = VertxNativeTypes.findNativeType(it.second.type.toTypeName())
                    val sqlParameter = query.find(param.name)!!
                    val paramName = param.variable.name?.asString()
                    if (nativeType == null) {
                        return@flatMap sequenceOf(Param(sqlParameter.sqlIndexes, param.name, CodeBlock.of("%N.apply(%N)", parameterMapperName(method, param.variable), paramName)))
                    } else {
                        return@flatMap sequenceOf(Param(sqlParameter.sqlIndexes, param.name, CodeBlock.of("%N", paramName)))
                    }
                }
                param as QueryParameter.EntityParameter
                return@flatMap param.entity.fields.asSequence().mapNotNull { field ->
                    val sqlParameter = query.find(param.name + "." + field.property.simpleName.asString())
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        return@mapNotNull null
                    }
                    val fieldName = field.property.simpleName.asString()
                    val variableName = param.variable.name?.asString() + "_" + fieldName
                    val fieldAccessor = CodeBlock.of("%N?.%N", it.first, fieldName)
                    val nativeType = VertxNativeTypes.findNativeType(field.type.toTypeName())
                    if (nativeType == null) {
                        val mapperName = parameterMapperName(method, param.variable, fieldName)
                        return@mapNotNull Param(sqlParameter.sqlIndexes, variableName, CodeBlock.of("%N.apply(%L)", mapperName, fieldAccessor))
                    } else {
                        return@mapNotNull Param(sqlParameter.sqlIndexes, variableName, fieldAccessor)
                    }
                }
            }
            .toList()
        for (sqlParam in sqlParams) {
            b.addStatement("val %N = %L", "_${sqlParam.name}", sqlParam.code)
        }
        val callParameters = sqlParams.asSequence()
            .flatMap { it.indexes.asSequence().map { i -> it.name to i } }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
        if (callParameters.isEmpty()) {
            b.addStatement("val _tuple = %T.tuple()", VertxTypes.tuple)
        } else {
            b.addCode("val _tuple = %T.of(", VertxTypes.tuple)
            for ((i, parameter) in callParameters.withIndex()) {
                if (i > 0) {
                    b.addCode(",")
                }
                b.addCode("\n  %N", "_$parameter")
            }
            b.addCode("\n)\n")
        }
        if (batchParam != null) {
            b.addStatement("_batchParams.add(_tuple)")
            b.endControlFlow()
        }
    }
}
