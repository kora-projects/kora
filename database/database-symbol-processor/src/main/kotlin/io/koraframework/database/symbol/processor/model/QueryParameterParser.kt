package io.koraframework.database.symbol.processor.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.CommonClassNames.isList
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.parseMappingData


object QueryParameterParser {
    fun parse(connectionType: ClassName, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction): List<QueryParameter> {
        return parse(listOf(connectionType), parameterMapperType, method, methodType)
    }

    fun parse(connectionType: ClassName, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): List<QueryParameter> {
        return parse(listOf(connectionType), parameterMapperType, method, methodType, repositoryType)
    }

    fun parse(connectionTypes: List<ClassName>, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction): List<QueryParameter> {
        return parse(connectionTypes, parameterMapperType, method, methodType, null)
    }

    fun parse(connectionTypes: List<ClassName>, parameterMapperType: ClassName, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType?): List<QueryParameter> {
        val result = ArrayList<QueryParameter>(method.parameters.size)
        for (i in method.parameters.indices) {
            val type = resolveTypeParameter(methodType.parameterTypes[i]!!, repositoryType, method.parameters[i].type)
            val parameter = this.parse(connectionTypes, parameterMapperType, method.parameters[i], type)
            result.add(parameter)
        }
        return result
    }

    private fun resolveTypeParameter(type: KSType, repositoryType: KSType?, originalType: KSTypeReference): KSType {
        if (repositoryType == null) {
            return type
        }

        val declaration = originalType.resolve().declaration
        if (declaration is KSClassDeclaration) {
            return originalType.resolve()
        }
        val typeParameter = if (declaration is KSTypeParameter) {
            declaration
        } else {
            type.declaration as? KSTypeParameter
        }
        if (typeParameter == null) {
            return type
        }

        return findGenericArgument(repositoryType, typeParameter.name.asString()) ?: type
    }

    private fun findGenericArgument(type: KSType, targetName: String): KSType? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        val index = declaration.typeParameters.indexOfFirst { it.name.asString() == targetName }
        if (index >= 0) {
            return type.arguments.getOrNull(index)?.type?.resolve()
        }

        return declaration.superTypes
            .map { it.resolve() }
            .firstNotNullOfOrNull { findGenericArgument(it, targetName) }
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
