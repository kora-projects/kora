package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.jdbc.JdbcNativeTypes
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames.isCollection
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.KspCommonUtils.getNameConverter
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.isJavaRecord

class QueryMacrosParser {
    companion object {
        private const val MACROS_START = "%{"
        private const val MACROS_END = "}"
        private const val TARGET_RETURN = "return"
        private const val SPECIAL_ID = "@id"
    }

    data class Target(val type: KSClassDeclaration, val name: String, val column: String?)
    data class Field(val field: KSPropertyDeclaration?, val column: String, val path: String, val isId: Boolean)

    fun parse(sql: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): String {
        val sqlBuilder = StringBuilder()
        var prevCmdIndex = 0
        while (true) {
            val cmdIndexStart = sql.indexOf(MACROS_START, prevCmdIndex)
            if (cmdIndexStart == -1) {
                return sqlBuilder.append(sql.substring(prevCmdIndex)).toString()
            }
            val cmdIndexEnd = sql.indexOf(MACROS_END, cmdIndexStart)
            val targetAndCmdAsStr = sql.substring(cmdIndexStart + 2, cmdIndexEnd)
            val substitution = getSubstitution(targetAndCmdAsStr, method, methodType, repositoryType)
            sqlBuilder.append(sql, prevCmdIndex, cmdIndexStart).append(substitution)
            prevCmdIndex = cmdIndexEnd + 1
        }
    }

    private fun getPathField(method: KSFunctionDeclaration, target: KSClassDeclaration, rootPath: String, columnPrefix: String): Sequence<Field> {
        val nativeType = JdbcNativeTypes.findNativeType(target.toClassName())
        if (nativeType != null) {
            throw ProcessingErrorException("Can't process argument '$rootPath' as macros cause it is Native Type: $target", method)
        }

        return getFields(target).flatMap { field ->
            val path = if (rootPath.isEmpty())
                field.simpleName.asString()
            else
                "$rootPath." + field.simpleName.asString()

            val isId = field.isAnnotationPresent(DbUtils.idAnnotation)
            val isEmbedded = field.annotations
                .filter { a -> DbUtils.embeddedAnnotation == a.annotationType.resolve().declaration.let { it as KSClassDeclaration }.toClassName() }
                .firstOrNull()

            if (isEmbedded != null) {
                val declaration = field.type.resolve().declaration
                if (declaration !is KSClassDeclaration) {
                    throw IllegalArgumentException("@Embedded annotation placed on field that can't be embedded: $target")
                }
                val prefix = isEmbedded.findValueNoDefault("value") ?: ""

                return@flatMap getPathField(method, declaration, path, prefix)
                    .map { f -> Field(f.field, f.column, f.path, isId) }
            } else {
                val columnName = getColumnName(target, field, columnPrefix)
                return@flatMap sequenceOf(Field(field, columnName, path, isId))
            }
        }
    }

    private fun getColumnName(target: KSClassDeclaration, field: KSPropertyDeclaration, columnPrefix: String): String {
        val nameConverter = target.getNameConverter(snakeCaseNameConverter)
        val columnAnnotation = field.findAnnotation(DbUtils.columnAnnotation)?.findValueNoDefault<String>("value")
        if (columnAnnotation != null) {
            return columnPrefix + columnAnnotation
        } else {
            return columnPrefix + nameConverter.convert(field.simpleName.asString())
        }
    }

    private fun getFields(method: KSFunctionDeclaration, target: Target): List<Field> {
        val nativeType = JdbcNativeTypes.findNativeType(target.type.toClassName())
        if (nativeType != null && target.column != null) {
            return listOf(Field(field = null, column = target.column, path = target.name, isId = false))
        }

        return getPathField(method, target.type, target.name, "").toList()
    }

    private fun getCommandSelectorPaths(type: KSClassDeclaration, rootPath: String, selects: String): Set<String> {
        val fields = getFields(type)
        return selects.trim().split(",".toRegex())
            .dropLastWhile { it.isEmpty() }
            .asSequence()
            .map { obj -> obj.trim() }
            .flatMap { fieldName ->
                val field = if (fieldName == SPECIAL_ID) {
                    fields.firstOrNull { f -> f.isAnnotationPresent(DbUtils.idAnnotation) }
                        ?: throw IllegalArgumentException("@Id annotated field not found, but was present in query marcos: " + selects.trim())
                } else {
                    fields.firstOrNull { it.simpleName.asString().contentEquals(fieldName) }
                        ?: throw IllegalArgumentException("Field '" + fieldName + "' not found, but was present in query marcos: " + selects.trim())
                }

                if (field.isAnnotationPresent(DbUtils.embeddedAnnotation)) {
                    val resolved = field.type.resolve().declaration
                    if (resolved is KSClassDeclaration) {
                        return@flatMap getFields(resolved)
                            .map { embeddedField -> rootPath + "." + field.simpleName.asString() + "." + embeddedField.simpleName.asString() }
                    } else {
                        throw IllegalArgumentException("@Id @Embedded annotated illegal field in query marcos: " + selects.trim())
                    }
                }

                return@flatMap sequenceOf(rootPath + "." + field.simpleName.asString())
            }
            .toSet()
    }

    private fun getFields(type: KSClassDeclaration): Sequence<KSPropertyDeclaration> {
        return if (type.isJavaRecord()) {
            parseRecordFields(type)
        } else {
            parseDataClassFields(type)
        }
    }

    private fun parseRecordFields(typeDecl: KSClassDeclaration) = typeDecl.getAllProperties()

    private fun parseDataClassFields(typeDecl: KSClassDeclaration) = typeDecl.getAllProperties()

    private fun getSubstitution(targetAndCommand: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): String {
        try {
            val targetAndCmd = targetAndCommand.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toList()
            if (targetAndCmd.size == 1) {
                throw ProcessingErrorException(
                    "Can't extract query marcos and target from: $targetAndCommand",
                    method
                )
            }
            val target = getTarget(targetAndCmd[0].trim(), method, methodType, repositoryType)
            var selectors = targetAndCmd[1].split("-=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val include: Boolean
            if (selectors.size == 1) {
                include = true
                selectors = targetAndCmd[1].split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                include = false
            }
            val commandAsStr = selectors[0].trim()

            val paths = if (selectors.size != 1)
                getCommandSelectorPaths(target.type, target.name, selectors[1])
            else
                setOf()

            val fields = if (paths.isEmpty()) {
                getFields(method, target)
            } else {
                getFields(method, target).filter { include == paths.contains(it.path) }
            }

            val nameConverter = target.type.getNameConverter(snakeCaseNameConverter)

            val tableName = target.type.findAnnotation(DbUtils.tableAnnotation)?.findValueNoDefault("value")
                ?: nameConverter.convert(target.type.simpleName.asString())

            return when (commandAsStr) {
                "table" -> tableName
                "selects" -> fields.joinToString(", ") { f -> f.column }
                "inserts" -> {
                    val tableAndColumnPrefix = fields.joinToString(", ", "$tableName(", ")") { it.column }
                    val inserts = fields.joinToString(", ", "VALUES (", ")") { ":" + it.path }

                    "$tableAndColumnPrefix $inserts"
                }

                "updates" -> fields.asSequence()
                    .filter { f: Field -> !f.isId }
                    .joinToString(", ") { f: Field -> f.column + " = :" + f.path }

                "where" -> fields.joinToString(" AND ") { it.column + " = :" + it.path }
                else -> throw ProcessingErrorException("Unknown query marcos specified: $targetAndCommand", method)
            }
        } catch (e: IllegalArgumentException) {
            throw ProcessingErrorException(e.message.toString(), method)
        }
    }

    private fun getTarget(targetName: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): Target {
        var reference: KSTypeReference? = null
        var type: KSType? = null

        if (TARGET_RETURN == targetName) {
            if (method.isVoid()) {
                throw ProcessingErrorException(
                    "Macros command specified 'return' target, but return value is type Void",
                    method
                )
            } else if (method.returnType?.toTypeName() == DbUtils.updateCount) {
                throw ProcessingErrorException(
                    "Macros command specified 'return' target, but return value is type UpdateCount",
                    method
                )
            }

            val resolved = method.returnType!!.resolve()
            if (method.isCompletionStage() || method.isMono() || method.isFlux() || resolved.isCollection()) {
                reference = resolved.arguments[0].type
                type = methodType.returnType!!.arguments[0].type!!.resolve()
            } else {
                reference = method.returnType
                type = methodType.returnType
            }
        } else {
            for (i in method.parameters.indices) {
                val parameter = method.parameters[i]
                if (parameter.name!!.asString().contentEquals(targetName)) {
                    val resolved = parameter.type.resolve()
                    if (resolved.isCollection()) {
                        reference = resolved.arguments[0].type
                        type = methodType.parameterTypes[i]!!.arguments[0].type!!.resolve()
                    } else {
                        reference = parameter.type
                        type = methodType.parameterTypes[i]
                    }
                    break
                }
            }

            if (type == null) {
                val genericType = getGenericTarget(targetName, method, methodType, repositoryType)
                if (genericType != null) {
                    return genericType
                }
                throw ProcessingErrorException(
                    "Macros command unspecified target received: $targetName",
                    method
                )
            }
        }

        val resolved = type!!.declaration
        return if (resolved is KSClassDeclaration) {
            Target(resolved, targetName, getColumnName(method, targetName, reference, type))
        } else {
            throw ProcessingErrorException(
                "Macros command unprocessable target type: $targetName",
                method
            )
        }
    }

    private fun getGenericTarget(targetName: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): Target? {
        getTypeParameterTargetName(method.returnType)?.takeIf { it == targetName }?.let {
            val type = unwrapReturnType(method, methodType.returnType!!)
            return targetFromType(targetName, type)
        }

        for (i in method.parameters.indices) {
            val parameterTargetName = getTypeParameterTargetName(method.parameters[i].type)
                ?: (methodType.parameterTypes[i]?.declaration as? KSTypeParameter)?.name?.asString()
            parameterTargetName?.takeIf { it == targetName }?.let {
                val type = unwrapParameterType(method.parameters[i].type.resolve(), methodType.parameterTypes[i]!!)
                return targetFromType(method.parameters[i].name!!.asString(), type)
            }
        }

        findGenericArgument(repositoryType, targetName)?.let {
            val parameter = method.parameters.firstOrNull { parameter ->
                parameter.type.resolve().declaration == it.declaration
            }
            return targetFromType(parameter?.name?.asString() ?: targetName, it)
        }

        val parent = method.parentDeclaration
        if (parent is KSClassDeclaration) {
            parent.typeParameters.firstOrNull { it.name.asString() == targetName } ?: return null
        } else {
            return null
        }

        val superType = findSuperType(repositoryType, parent) ?: return null
        val index = parent.typeParameters.indexOfFirst { it.name.asString() == targetName }
        val type = superType.arguments.getOrNull(index)?.type?.resolve() ?: return null
        return targetFromType(targetName, type)
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

    private fun targetFromType(targetName: String, type: KSType): Target {
        val resolved = type.declaration
        return if (resolved is KSClassDeclaration) {
            Target(resolved, targetName, getColumnName(method = null, targetName, typeReference = null, type))
        } else {
            throw ProcessingErrorException("Macros command unprocessable target type: $targetName", resolved)
        }
    }

    private fun getTypeParameterTargetName(typeReference: KSTypeReference?): String? {
        val declaration = typeReference?.resolve()?.declaration
        return if (declaration is KSTypeParameter) {
            declaration.name.asString()
        } else {
            null
        }
    }

    private fun unwrapReturnType(method: KSFunctionDeclaration, returnType: KSType): KSType {
        val original = method.returnType!!.resolve()
        return if (method.isCompletionStage() || method.isMono() || method.isFlux() || original.isCollection()) {
            returnType.arguments[0].type!!.resolve()
        } else {
            returnType
        }
    }

    private fun unwrapParameterType(originalType: KSType, parameterType: KSType): KSType {
        return if (originalType.isCollection()) {
            parameterType.arguments[0].type!!.resolve()
        } else {
            parameterType
        }
    }

    private fun findSuperType(type: KSType, expectedDeclaration: KSClassDeclaration): KSType? {
        if (type.declaration == expectedDeclaration) {
            return type
        }

        val declaration = type.declaration as? KSClassDeclaration ?: return null
        return declaration.superTypes
            .map { it.resolve() }
            .firstNotNullOfOrNull { findSuperType(it, expectedDeclaration) }
    }

    private fun getColumnName(method: KSFunctionDeclaration?, targetName: String, typeReference: KSTypeReference?, type: KSType): String? {
        if (TARGET_RETURN != targetName) {
            method?.parameters
                ?.asSequence()
                ?.firstOrNull { it.name!!.asString().contentEquals(targetName) }
                ?.findAnnotation(DbUtils.columnAnnotation)
                ?.findValueNoDefault<String>("value")
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        return (typeReference?.resolve()?.annotations ?: type.annotations)
            .firstOrNull { DbUtils.columnAnnotation == (it.annotationType.resolve().declaration as KSClassDeclaration).toClassName() }
            ?.findValueNoDefault<String>("value")
            ?.takeIf { it.isNotEmpty() }
    }
}
