package ru.tinkoff.kora.database.symbol.processor.vertx

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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
        val tupleSize = parameters.asSequence().flatMap { if (it is QueryParameter.EntityParameter) it.entity.fields.asSequence() else sequenceOf() }.count()
        b.addStatement("val _tuple = %T(%L)", VertxTypes.arrayTuple, tupleSize)
        for (i in parameters.indices) {
            var parameter = parameters[i]
            if (parameter is QueryParameter.ConnectionParameter) {
                continue
            }
            var parameterName = parameter.name
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
                parameterName = "_batch_${parameter.name}"
            }
            if (parameter is QueryParameter.ParameterWithMapper) {
                val mapperName = parameterMapperName(method, parameter.variable)
                b.addStatement("_tuple.addValue(%N.apply(%N))", mapperName, parameterName)
            }
            if (parameter is QueryParameter.SimpleParameter) {
                b.addStatement("_tuple.addValue(%L)", parameterName)
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (field in parameter.entity.fields) {
                    val fieldName = field.property.simpleName.getShortName()
                    val sqlParameter = query.find(parameter.name + "." + fieldName)
                    if (sqlParameter == null) {
                        continue
                    }
                    val mapperName = parameterMapperName(method, parameter.variable, fieldName)
                    val mapper = field.mapping.getMapping(VertxTypes.parameterColumnMapper)
                    if (mapper != null) {
                        b.addStatement("_tuple.addValue(%N.apply(%L))", mapperName, "$parameterName?.$fieldName")
                        continue
                    }

                    val nativeType = VertxNativeTypes.findNativeType(field.type.toTypeName())
                    if (nativeType != null) {
                        b.addStatement("_tuple.addValue(%L)", "$parameterName?.$fieldName")
                        continue
                    }
                    b.addStatement("_tuple.addValue(%N.apply(%L))", mapperName, "$parameterName?.$fieldName")
                }
            }
        }
        if (batchParam != null) {
            b.addStatement("_batchParams.add(_tuple)")
            b.endControlFlow()
        }
    }
}
