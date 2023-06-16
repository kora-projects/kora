package ru.tinkoff.kora.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseMappingData


object QueryParameterParser {
    fun parse(connectionType: ClassName, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction): List<QueryParameter> {
        return parse(listOf(connectionType), parameterMapperType, method, methodType)
    }

    fun parse(connectionTypes: List<ClassName>, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction): List<QueryParameter> {
        val result = ArrayList<QueryParameter>(method.parameters.size)
        for (i in method.parameters.indices) {
            val parameter = this.parse(connectionTypes, parameterMapperType, method.parameters[i], methodType.parameterTypes[i]!!)
            result.add(parameter)
        }
        return result
    }

    private fun parse(connectionTypes: List<ClassName>, parameterMapperType: ClassName, parameter: KSValueParameter, type: KSType): QueryParameter {
        val name = parameter.name!!.getShortName();
        val typeName = type.toTypeName();
        if (connectionTypes.contains(typeName)) {
            return QueryParameter.ConnectionParameter(name, type, parameter);
        }
        val batch = parameter.findAnnotation(DbUtils.batchAnnotation);
        val mapping = parameter.parseMappingData().getMapping(parameterMapperType)
        if (batch != null) {
            if (typeName !is ParameterizedTypeName || !type.isList()) {
                throw ProcessingErrorException("@Batch parameter must be a list", parameter);
            }

            val batchType = type.arguments[0].type!!.resolve()
            val param = if (mapping != null) {
                QueryParameter.SimpleParameter(name, batchType, parameter)
            } else {
                val entity = DbEntity.parseEntity(batchType)
                if (entity != null) {
                    QueryParameter.EntityParameter(name, batchType, parameter, entity)
                } else {
                    QueryParameter.SimpleParameter(name, batchType, parameter)
                }
            }
            return QueryParameter.BatchParameter(name, type, parameter, param)
        }
        if (mapping != null) {
            return QueryParameter.SimpleParameter(name, type, parameter)
        }
        val entity = DbEntity.parseEntity(type);
        if (entity != null) {
            return QueryParameter.EntityParameter(name, type, parameter, entity)
        } else {
            return QueryParameter.SimpleParameter(name, type, parameter)
        }
    }

}
