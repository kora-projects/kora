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

    data class Target(val type: KSClassDeclaration, val name: String, val column: String?, val columnPrefix: String)
    data class Field(
        val field: KSPropertyDeclaration?,
        val column: String,
        val rawColumn: String,
        val path: String,
        val targetPath: String,
        val isId: Boolean
    )

    data class Command(val target: String, val command: String, val alias: String?)

    fun parse(sql: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): String {
        val aliases = collectAliases(sql, method, methodType, repositoryType)
        val sqlBuilder = StringBuilder()
        var prevCmdIndex = 0
        while (true) {
            val cmdIndexStart = sql.indexOf(MACROS_START, prevCmdIndex)
            if (cmdIndexStart == -1) {
                return sqlBuilder.append(sql.substring(prevCmdIndex)).toString()
            }
            val cmdIndexEnd = sql.indexOf(MACROS_END, cmdIndexStart)
            val targetAndCmdAsStr = sql.substring(cmdIndexStart + 2, cmdIndexEnd)
            val substitution = getSubstitution(targetAndCmdAsStr, method, methodType, repositoryType, aliases)
            sqlBuilder.append(sql, prevCmdIndex, cmdIndexStart).append(substitution)
            prevCmdIndex = cmdIndexEnd + 1
        }
    }

    private fun collectAliases(sql: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): Map<String, String> {
        val aliases = HashMap<String, String>()
        var prevCmdIndex = 0
        while (true) {
            val cmdIndexStart = sql.indexOf(MACROS_START, prevCmdIndex)
            if (cmdIndexStart == -1) {
                return aliases
            }
            val cmdIndexEnd = sql.indexOf(MACROS_END, cmdIndexStart)
            val targetAndCmdAsStr = sql.substring(cmdIndexStart + 2, cmdIndexEnd)
            val command = parseCommand(targetAndCmdAsStr, method)
            if (command.command == "table" && command.alias != null) {
                getTarget(command.target, method, methodType, repositoryType)
                aliases[command.target] = command.alias
            }
            prevCmdIndex = cmdIndexEnd + 1
        }
    }

    private fun parseCommand(targetAndCommand: String, method: KSFunctionDeclaration): Command {
        val targetAndCmd = targetAndCommand.split("#", limit = 2)
        if (targetAndCmd.size == 1) {
            throw ProcessingErrorException(macroParseError(targetAndCommand), method)
        }

        val target = targetAndCmd[0].trim()
        var selectors = targetAndCmd[1].split("-=", limit = 2)
        if (selectors.size == 1) {
            selectors = targetAndCmd[1].split("=", limit = 2)
        }

        val commandAsStr = selectors[0].trim()
        val lowerCommand = commandAsStr.lowercase()
        if (lowerCommand.startsWith("table as ")) {
            val alias = commandAsStr.substring("table as ".length).trim()
            if (alias.isEmpty()) {
                throw ProcessingErrorException(tableAliasError(targetAndCommand), method)
            }
            return Command(target, "table", alias)
        }
        return Command(target, lowerCommand, null)
    }

    private fun getPathField(
        method: KSFunctionDeclaration,
        target: KSClassDeclaration,
        rootPath: String,
        rootTargetPath: String,
        columnPrefix: String
    ): Sequence<Field> {
        val nativeType = JdbcNativeTypes.findNativeType(target.toClassName())
        if (nativeType != null) {
            throw ProcessingErrorException(nativeTypeMacroError(rootPath, target), method)
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
                val fieldType = field.type.resolve()
                val embeddedDeclaration = if (fieldType.isCollection()) {
                    fieldType.arguments[0].type!!.resolve().declaration
                } else {
                    declaration
                }
                if (embeddedDeclaration !is KSClassDeclaration) {
                    throw IllegalStateException(embeddedFieldError(target))
                }
                val prefix = isEmbedded.findValueNoDefault("value") ?: ""
                val targetPath = if (rootTargetPath.isEmpty())
                    field.simpleName.asString()
                else
                    "$rootTargetPath." + field.simpleName.asString()

                return@flatMap getPathField(method, embeddedDeclaration, path, targetPath, columnPrefix + prefix)
                    .map { f -> Field(f.field, f.column, f.rawColumn, f.path, f.targetPath, isId || f.isId) }
            } else {
                val rawColumnName = getColumnName(target, field)
                val columnName = if (columnPrefix.isBlank()) rawColumnName else columnPrefix + rawColumnName
                return@flatMap sequenceOf(Field(field, columnName, rawColumnName, path, rootTargetPath, isId))
            }
        }
    }

    private fun getColumnName(target: KSClassDeclaration, field: KSPropertyDeclaration): String {
        val nameConverter = target.getNameConverter(snakeCaseNameConverter)
        val columnAnnotation = field.findAnnotation(DbUtils.columnAnnotation)?.findValueNoDefault<String>("value")
        if (columnAnnotation != null) {
            return columnAnnotation
        } else {
            return nameConverter.convert(field.simpleName.asString())
        }
    }

    private fun getFields(method: KSFunctionDeclaration, target: Target): List<Field> {
        val nativeType = JdbcNativeTypes.findNativeType(target.type.toClassName())
        if (nativeType != null && target.column != null) {
            return listOf(Field(field = null, column = target.column, rawColumn = target.column, path = target.name, targetPath = target.name, isId = false))
        }

        return getPathField(method, target.type, target.name, target.name, target.columnPrefix).toList()
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
                        ?: throw IllegalStateException(idFieldError(selects))
                } else {
                    fields.firstOrNull { it.simpleName.asString().contentEquals(fieldName) }
                        ?: throw IllegalStateException(fieldSelectorError(fieldName, selects))
                }

                if (field.isAnnotationPresent(DbUtils.embeddedAnnotation)) {
                    val resolved = field.type.resolve().declaration
                    if (resolved is KSClassDeclaration) {
                        return@flatMap getFields(resolved)
                            .map { embeddedField -> rootPath + "." + field.simpleName.asString() + "." + embeddedField.simpleName.asString() }
                    } else {
                        throw IllegalStateException(idEmbeddedError(selects))
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

    private fun getSubstitution(
        targetAndCommand: String,
        method: KSFunctionDeclaration,
        methodType: KSFunction,
        repositoryType: KSType,
        aliases: Map<String, String>
    ): String {
        try {
            val targetAndCmd = targetAndCommand.split("#", limit = 2)
            if (targetAndCmd.size == 1) {
                throw ProcessingErrorException(macroParseError(targetAndCommand), method)
            }
            val target = getTarget(targetAndCmd[0].trim(), method, methodType, repositoryType)
            val command = parseCommand(targetAndCommand, method)
            var selectors = targetAndCmd[1].split("-=", limit = 2).toTypedArray()
            val include: Boolean
            if (selectors.size == 1) {
                include = true
                selectors = targetAndCmd[1].split("=", limit = 2).toTypedArray()
            } else {
                include = false
            }
            val commandAsStr = command.command

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
                "table" -> if (command.alias == null) tableName else "$tableName ${command.alias}"
                "selects" -> fields.joinToString(", ") { f -> selectExpression(f, aliases) }
                "columns" -> fields.joinToString(", ") { f -> f.column }
                "values" -> fields.joinToString(", ") { f -> ":" + f.path }
                "inserts" -> {
                    val tableAndColumnPrefix = fields.joinToString(", ", "$tableName(", ")") { it.column }
                    val inserts = fields.joinToString(", ", "VALUES (", ")") { ":" + it.path }

                    "$tableAndColumnPrefix $inserts"
                }

                "updates" -> fields.asSequence()
                    .filter { f: Field -> !f.isId }
                    .joinToString(", ") { f: Field -> f.column + " = :" + f.path }

                "where" -> fields.joinToString(" AND ") { columnReference(it, aliases) + " = :" + it.path }
                else -> throw ProcessingErrorException(unknownMacroError(targetAndCommand), method)
            }
        } catch (e: IllegalArgumentException) {
            throw ProcessingErrorException(e.message.toString(), method)
        }
    }

    private fun selectExpression(field: Field, aliases: Map<String, String>): String {
        val reference = columnReference(field, aliases)
        if (aliases.containsKey(field.targetPath) && field.column != field.rawColumn) {
            return "$reference AS ${field.column}"
        }
        return reference
    }

    private fun columnReference(field: Field, aliases: Map<String, String>): String {
        val alias = aliases[field.targetPath] ?: return field.column
        return "$alias.${field.rawColumn}"
    }

    private fun getTarget(targetName: String, method: KSFunctionDeclaration, methodType: KSFunction, repositoryType: KSType): Target {
        val path = targetName.split(".")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (path.isEmpty()) {
            throw ProcessingErrorException(unspecifiedTargetError(targetName), method)
        }

        var reference: KSTypeReference? = null
        var type: KSType? = null
        val rootTargetName = path[0]

        if (TARGET_RETURN == rootTargetName) {
            if (method.isVoid()) {
                throw ProcessingErrorException(invalidReturnTargetError("Void"), method)
            } else if (method.returnType?.toTypeName() == DbUtils.updateCount) {
                throw ProcessingErrorException(invalidReturnTargetError("UpdateCount"), method)
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
                if (parameter.name!!.asString().contentEquals(rootTargetName)) {
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
                val genericType = getGenericTarget(rootTargetName, method, methodType, repositoryType)
                if (genericType != null) {
                    return genericType
                }
                throw ProcessingErrorException(unspecifiedTargetError(rootTargetName), method)
            }
        }

        val resolved = type!!.declaration
        return if (resolved is KSClassDeclaration) {
            var target = Target(resolved, rootTargetName, getColumnName(method, rootTargetName, reference, type), "")
            for (i in 1 until path.size) {
                target = getChildTarget(method, target, path[i])
            }
            target
        } else {
            throw ProcessingErrorException(unprocessableTargetError(targetName), method)
        }
    }

    private fun getChildTarget(method: KSFunctionDeclaration, target: Target, childName: String): Target {
        for (field in getFields(target.type)) {
            if (!field.simpleName.asString().contentEquals(childName)) {
                continue
            }
            val declaration = field.type.resolve().declaration
            val fieldType = field.type.resolve()
            val targetDeclaration = if (fieldType.isCollection()) {
                fieldType.arguments[0].type!!.resolve().declaration
            } else {
                declaration
            }
            if (targetDeclaration is KSClassDeclaration) {
                val prefix = field.findAnnotation(DbUtils.embeddedAnnotation)?.findValueNoDefault<String>("value") ?: ""
                return Target(targetDeclaration, "${target.name}.$childName", null, target.columnPrefix + prefix)
            }
            throw ProcessingErrorException(unprocessableTargetError("${target.name}.$childName"), method)
        }
        throw ProcessingErrorException(childFieldNotFoundError(childName, target.name), method)
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
            Target(resolved, targetName, getColumnName(method = null, targetName, typeReference = null, type), "")
        } else {
            throw ProcessingErrorException(unprocessableTargetError(targetName), resolved)
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

    private fun macroParseError(targetAndCommand: String): String {
        return """
            Invalid query macro syntax: `$targetAndCommand`

            A query macro must have a target and a command separated by `#`.
            Expected format: `${'$'}{target#command}` or `${'$'}{target#command=field1, field2}`.
            Examples: `${'$'}{entity#selects}`, `${'$'}{entity#where=@id}`, `${'$'}{return#columns}`.

            ${macroReference()}

            Fix: add the missing `#command` part or remove the macro from the query.
        """.trimIndent()
    }

    private fun tableAliasError(targetAndCommand: String): String {
        return """
            Invalid query macro table alias: `$targetAndCommand`

            The `table as` command requires a non-empty SQL alias.
            Expected format: `${'$'}{target#table as alias}`.
            Example: `${'$'}{entity#table as e}`.

            Fix: specify an alias after `table as`, or use `${'$'}{target#table}` without an alias.
        """.trimIndent()
    }

    private fun nativeTypeMacroError(rootPath: String, target: KSClassDeclaration): String {
        return """
            Query macro target `$rootPath` has unsupported type `$target`.

            Macros such as `selects`, `columns`, `values`, `updates`, and `where` expand fields of an entity type.
            The selected target is a native JDBC type, so there are no entity fields to expand.

            ${macroReference()}

            Fix: point the macro to an entity parameter/return value, or use a regular SQL parameter for this value.
        """.trimIndent()
    }

    private fun embeddedFieldError(target: KSClassDeclaration): String {
        return """
            Invalid `@Embedded` field in query macro target `$target`.

            `@Embedded` can be expanded only when the annotated field is an entity-like declared type.

            Fix: change the embedded field type to an entity class/data class, or remove `@Embedded` from this field.
        """.trimIndent()
    }

    private fun idFieldError(selects: String): String {
        return """
            Query macro selector `@id` cannot be resolved.

            Selector list: `${selects.trim()}`
            The target entity does not have a field annotated with `@Id`.

            Fix: annotate the id field with `@Id`, or replace `@id` with an existing field name.
        """.trimIndent()
    }

    private fun fieldSelectorError(fieldName: String, selects: String): String {
        return """
            Query macro selector `$fieldName` cannot be resolved.

            Selector list: `${selects.trim()}`
            The selector must match a field name on the macro target entity.

            Fix: rename the selector to an existing field, or remove it from the macro.
        """.trimIndent()
    }

    private fun idEmbeddedError(selects: String): String {
        return """
            Query macro selector contains an invalid `@Id @Embedded` field.

            Selector list: `${selects.trim()}`
            The selected id field is marked `@Embedded`, but its type cannot be expanded as an entity.

            Fix: make the embedded id type an entity-like declared type, or select concrete fields explicitly.
        """.trimIndent()
    }

    private fun unknownMacroError(targetAndCommand: String): String {
        return """
            Unknown query macro command: `$targetAndCommand`

            ${macroReference()}

            Fix: use one of the supported commands or remove the macro from the SQL query.
        """.trimIndent()
    }

    private fun unspecifiedTargetError(targetName: String): String {
        return """
            Query macro target `$targetName` cannot be resolved.

            ${macroReference()}

            Fix: use an existing method parameter name, use `return` for the result type, or correct the target path.
        """.trimIndent()
    }

    private fun invalidReturnTargetError(returnType: String): String {
        return """
            Query macro target `return` cannot be used here.

            The repository method return type is `$returnType`, so there is no entity result to expand.

            ${macroReference()}

            Fix: point the macro to an entity parameter, or change the method return type to an entity/collection of entities.
        """.trimIndent()
    }

    private fun unprocessableTargetError(targetName: String): String {
        return """
            Query macro target `$targetName` cannot be expanded.

            The resolved target is not an entity-like declared type with fields.

            ${macroReference()}

            Fix: point the macro to an entity parameter/return value, or select a nested entity field path.
        """.trimIndent()
    }

    private fun childFieldNotFoundError(childName: String, targetName: String): String {
        return """
            Query macro target path `$targetName.$childName` cannot be resolved.

            Field `$childName` was not found on target `$targetName`.

            ${macroReference()}

            Fix: use an existing field name in the macro target path, or remove the nested path segment.
        """.trimIndent()
    }

    private fun macroReference(): String {
        return """
            Available query macros:
            - `${'$'}{target#table}`: expands to the target table name.
            - `${'$'}{target#table as alias}`: expands to the target table name with an SQL alias.
            - `${'$'}{target#selects}`: expands to selected columns, including SQL aliases when needed.
            - `${'$'}{target#columns}`: expands to column names.
            - `${'$'}{target#values}`: expands to named bind parameters.
            - `${'$'}{target#inserts}`: expands to `table(columns) VALUES (:values)`.
            - `${'$'}{target#updates}`: expands to `column = :field` assignments, excluding `@Id`.
            - `${'$'}{target#where}`: expands to `column = :field` predicates joined with `AND`.

            A macro target can be:
            - a method argument name, for example `${'$'}{entity#columns}`;
            - `return`, for example `${'$'}{return#selects}`;
            - a generic type parameter resolved from the repository or method, for example `${'$'}{T#columns}`.

            Selectors can include fields with `=` or exclude fields with `-=`.
            Example: `${'$'}{entity#selects=id, name}` or `${'$'}{entity#updates-=createdAt}`.
        """.trimIndent()
    }
}
