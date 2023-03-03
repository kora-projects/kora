package ru.tinkoff.kora.database.symbol.processor.vertx

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.QueryWithParameters
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.parseMappingData

object ParametersToTupleBuilder {
    fun generate(b: FunSpec.Builder, query: QueryWithParameters, method: KSFunctionDeclaration, parameters: List<QueryParameter>, batchParam: QueryParameter?, parameterMappers: FieldFactory) {
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
                    val mapping = it.second.variable.parseMappingData().getMapping(VertxTypes.parameterColumnMapper)
                    val sqlParameter = query.find(param.name)!!
                    if (nativeType != null && mapping == null) {
                        return@flatMap sequenceOf(Param(sqlParameter.sqlIndexes, param.name, CodeBlock.of("%N", it.first)))
                    } else if (mapping?.mapper != null) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tags)
                        return@flatMap sequenceOf(Param(sqlParameter.sqlIndexes, param.name, CodeBlock.of("%N.apply(%N)", mapperName, it.first)))
                    } else {
                        val mapperName = parameterMappers.get(VertxTypes.parameterColumnMapper, param.type, param.variable)
                        return@flatMap sequenceOf(Param(sqlParameter.sqlIndexes, param.name, CodeBlock.of("%N.apply(%N)", mapperName, it.first)))
                    }
                }
                param as QueryParameter.EntityParameter
                return@flatMap param.entity.fields.asSequence().mapNotNull { field ->
                    val sqlParameter = query.find(param.name + "." + field.property.simpleName.asString())
                    if (sqlParameter == null || sqlParameter.sqlIndexes.isEmpty()) {
                        return@mapNotNull null
                    }
                    val fieldName = field.property.simpleName.asString()
                    val variableName = it.first + "_" + fieldName
                    val fieldAccessor = CodeBlock.of("%N?.%N", it.first, fieldName)
                    val nativeType = VertxNativeTypes.findNativeType(field.type.toTypeName())
                    val mapping = field.mapping.getMapping(VertxTypes.parameterColumnMapper)
                    if (nativeType != null && mapping == null) {
                        return@mapNotNull Param(sqlParameter.sqlIndexes, variableName, fieldAccessor)
                    } else if (mapping?.mapper != null) {
                        val mapperName = parameterMappers.get(mapping.mapper!!, mapping.tags)
                        return@mapNotNull Param(sqlParameter.sqlIndexes, variableName, CodeBlock.of("%N.apply(%L)", mapperName, fieldAccessor))
                    } else {
                        val mapperName = parameterMappers.get(VertxTypes.parameterColumnMapper, field.type, field.property)
                        return@mapNotNull Param(sqlParameter.sqlIndexes, variableName, CodeBlock.of("%N.apply(%L)", mapperName, fieldAccessor))
                    }
                }
            }
            .toList()
        for (sqlParam in sqlParams) {
            b.addStatement("val %N = %L", "_${sqlParam.name}", sqlParam.code)
        }
        if (sqlParams.isEmpty()) {
            b.addStatement("val _tuple = %T.tuple()", VertxTypes.tuple)
        } else {
            b.addCode("val _tuple = %T.of(", VertxTypes.tuple)
            for ((i, parameter) in sqlParams.withIndex()) {
                if (i > 0) {
                    b.addCode(",")
                }
                b.addCode("\n  %N", "_${parameter.name}")
            }
            b.addCode("\n)\n")
        }
        if (batchParam != null) {
            b.addStatement("_batchParams.add(_tuple)")
            b.endControlFlow()
        }
    }
}
