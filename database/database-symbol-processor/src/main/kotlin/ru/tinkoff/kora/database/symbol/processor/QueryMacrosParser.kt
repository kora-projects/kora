package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter
import ru.tinkoff.kora.database.symbol.processor.jdbc.JdbcNativeTypes
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.isJavaRecord

class QueryMacrosParser {
    companion object {
        private const val MACROS_START = "%{"
        private const val MACROS_END = "}"
        private const val TARGET_RETURN = "return"
        private const val SPECIAL_ID = "@id"
    }

    data class Target(val typeRef: KSTypeReference, val type: KSClassDeclaration, val annotated: KSAnnotated, val name: String)

    data class Field(val column: String, val path: String, val isId: Boolean, val isEmbedded: Boolean)

    fun parse(sql: String, method: KSFunctionDeclaration): String {
        val sqlBuilder = StringBuilder()
        var prevCmdIndex = 0
        while (true) {
            val cmdIndexStart = sql.indexOf(MACROS_START, prevCmdIndex)
            if (cmdIndexStart == -1) {
                return sqlBuilder.append(sql.substring(prevCmdIndex)).toString()
            }
            val cmdIndexEnd = sql.indexOf(MACROS_END, cmdIndexStart)
            val targetAndCmdAsStr = sql.substring(cmdIndexStart + 2, cmdIndexEnd)
            val substitution = getSubstitution(targetAndCmdAsStr, method)
            sqlBuilder.append(sql, prevCmdIndex, cmdIndexStart).append(substitution)
            prevCmdIndex = cmdIndexEnd + 1
        }
    }

    private fun getPathField(
        method: KSFunctionDeclaration,
        target: KSClassDeclaration,
        targetRef: KSTypeReference,
        targetAnnotated: KSAnnotated,
        rootPath: String,
        columnPrefix: String
    ): Sequence<Field> {
        val treatAsNativeParameterColumn = targetRef.annotations
            .filter { a -> a.annotationType.resolve().toClassName() == DbUtils.columnAnnotation }
            .firstOrNull()
            ?: targetAnnotated.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == DbUtils.columnAnnotation }
                .firstOrNull()

        if (treatAsNativeParameterColumn != null) {
            val value = treatAsNativeParameterColumn.arguments[0].value
            if (value != null) {
                return sequenceOf(
                    Field(
                        value.toString(),
                        rootPath,
                        isId = false,
                        isEmbedded = false
                    )
                )
            }

            throw ProcessingErrorException("Can't treat argument '$rootPath' as macros native cause failed to extract @Column value: $target", method)
        }

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
                .filter { a -> DbUtils.embeddedAnnotation == a.annotationType.resolve().toClassName() }
                .firstOrNull()

            if (isEmbedded != null) {
                val declaration = field.type.resolve().declaration
                if (declaration !is KSClassDeclaration) {
                    throw IllegalArgumentException("@Embedded annotation placed on field that can't be embedded: $target")
                }
                val prefix = isEmbedded.findValueNoDefault("value") ?: ""

                val pathFields = getPathField(method, declaration, field.type, field, path, prefix)
                return@flatMap pathFields
                    .map { f -> Field(f.column, f.path, isId, true) }
            } else {
                val columnName = getColumnName(target, field, columnPrefix)
                return@flatMap sequenceOf(Field(columnName, path, isId, false))
            }
        }
    }

    private fun getColumnName(target: KSClassDeclaration, field: KSPropertyDeclaration, columnPrefix: String): String {
        val nameConverter = target.getNameConverter(SnakeCaseNameConverter.INSTANCE)
        val columnAnnotation = field.findAnnotation(DbUtils.columnAnnotation)?.findValueNoDefault<String>("value")
        if (columnAnnotation != null) {
            return columnPrefix + columnAnnotation
        } else {
            return columnPrefix + nameConverter.convert(field.simpleName.asString())
        }
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

    private fun getSubstitution(targetAndCommand: String, method: KSFunctionDeclaration): String {
        try {
            val targetAndCmd = targetAndCommand.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toList()
            if (targetAndCmd.size == 1) {
                throw ProcessingErrorException(
                    "Can't extract query marcos and target from: $targetAndCommand",
                    method
                )
            }
            val target = getTarget(targetAndCmd[0].trim(), method)
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
                getPathField(method, target.type, target.typeRef, target.annotated, target.name, "").toList()
            } else {
                getPathField(method, target.type, target.typeRef, target.annotated, target.name, "").filter { include == paths.contains(it.path) }.toList()
            }

            val nameConverter = target.type.getNameConverter(SnakeCaseNameConverter.INSTANCE)

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

    private fun getTarget(targetName: String, method: KSFunctionDeclaration): Target {
        val refPair: Pair<KSTypeReference, KSAnnotated>

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
            refPair = if (method.isCompletionStage() || method.isMono() || method.isFlux() || resolved.isCollection()) {
                Pair(resolved.arguments[0].type!!, method)
            } else {
                Pair(method.returnType!!, method)
            }
        } else {
            refPair = method.parameters
                .filter { p -> p.name!!.asString().contentEquals(targetName) }
                .map { param ->
                    val resolved = param.type.resolve()
                    if (resolved.isCollection()) {
                        Pair(resolved.arguments[0].type!!, param as KSAnnotated)
                    } else {
                        Pair(param.type, param as KSAnnotated)
                    }
                }
                .firstOrNull()
                ?: throw ProcessingErrorException(
                    "Macros command unspecified target received: $targetName",
                    method
                )
        }

        val resolved = refPair.first.resolve().declaration
        return if (resolved is KSClassDeclaration) {
            Target(refPair.first, resolved, refPair.second, targetName)
        } else {
            throw ProcessingErrorException(
                "Macros command unprocessable target type: $targetName",
                method
            )
        }
    }
}
