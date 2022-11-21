package ru.tinkoff.kora.database.symbol.processor.model
/*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.database.common.SubEntityOf
import ru.tinkoff.kora.database.common.annotation.Id
import ru.tinkoff.kora.database.common.annotation.Table
import ru.tinkoff.kora.database.symbol.processor.findEntityConstructor
import ru.tinkoff.kora.database.symbol.processor.parseColumnName
import ru.tinkoff.kora.ksp.common.getNameConverter
import java.util.*
import java.util.regex.Pattern

data class TemplateModel(val declaration: KSClassDeclaration, val tableName: String, val columns: LinkedHashMap<String, ColumnModel>) {
    data class ColumnModel(val field: KSPropertyDeclaration, val type: KSType, val columnName: String, val isId: Boolean)

    data class TemplateParam(val paramName: String?, val template: String, val rawTemplate: String)

    /**
     * {table_name} <br/>
     * {id_field} <br/>
     * {id_column} <br/>
     * {id_field_*} <br/>
     * {id_column_*} <br/>
     * {columns} <br/>
     * {non_id_columns} <br/>
     * <br/>
     * {non_id_placeholders} <br/>
     * {update_columns} <br/>
     */

    fun getTemplateParams(entityParamName: String?): MutableMap<String, String> {
        val result = hashMapOf<String, String>()
        result["table_name"] = tableName
        val nonIdColumns = mutableListOf<ColumnModel>()
        val columns = mutableListOf<String>()
        run {
            var idColumnsCounter = 0
            for (column in this.columns.values) {
                columns.add(column.columnName)
                if (column.isId) {
                    idColumnsCounter++
                    result["id_field_$idColumnsCounter"] = column.field.simpleName.asString()
                    result["id_column_$idColumnsCounter"] = column.columnName
                    if (idColumnsCounter == 1) {
                        result["id_field"] = column.field.simpleName.asString()
                        result["id_column"] = column.columnName
                    }
                } else {
                    nonIdColumns.add(column)
                }
            }
        }
        result["columns"] = java.lang.String.join(", ", columns)
        result["non_id_columns"] = nonIdColumns.joinToString(", ") { obj: ColumnModel -> obj.columnName }
        if (entityParamName != null) {
            val nonIdPlaceholders = ArrayList<String>()
            for (nonIdColumn in nonIdColumns) {
                nonIdPlaceholders.add(":%s.%s".format(entityParamName, nonIdColumn.field.simpleName.asString()))
            }
            result["non_id_placeholders"] = java.lang.String.join(", ", nonIdPlaceholders)
            val updateColumn = ArrayList<String>()
            for (nonIdColumn in nonIdColumns) {
                updateColumn.add("%s = :%s.%s".format(nonIdColumn.columnName, entityParamName, nonIdColumn.field.simpleName.asString()))
            }
            result["update_columns"] = java.lang.String.join(", ", updateColumn)
        }
        return result
    }

    @KspExperimental
    companion object {
        fun parseEntityModel(resolver: Resolver, declaration: KSClassDeclaration): TemplateModel {
            val fullEntity = findFullEntity(resolver, declaration)
            val targetType = fullEntity ?: declaration
            val tableName: String = parseTableName(targetType)
            val columnsNameConverter = getNameConverter(targetType)
            val columns: LinkedHashMap<String, ColumnModel> = parseColumns(targetType, columnsNameConverter)

            val resultColumns = fullEntity?.let {
                parseColumns(it, columnsNameConverter).keys
            }?.let { partialTypeColumns ->
                linkedMapOf(*columns.keys
                    .filter { c -> partialTypeColumns.contains(c) || columns[c]!!.isId }
                    .map { it to columns[it]!! }
                    .toTypedArray())
            } ?: columns

            return TemplateModel(declaration, tableName, resultColumns)
        }

        fun detectTemplateParams(sql: String): List<TemplateParam> {
            val processedTemplates = HashSet<String>()
            val result = ArrayList<TemplateParam>()
            val paramRegex = Pattern.compile("(?<raw>\\{((?<paramName>[\\w_][\\w\\d_]*)\\.)?(?<template>[\\w\\d_]+)})")
            val matcher = paramRegex.matcher(sql)
            while (matcher.find()) {
                val rawTemplate = matcher.group("raw")
                if (processedTemplates.contains(rawTemplate)) {
                    continue
                }
                processedTemplates.add(rawTemplate)
                result.add(TemplateParam(matcher.group("paramName"), matcher.group("template"), rawTemplate))
            }
            return result
        }

        private fun findFullEntity(resolver: Resolver, declaration: KSClassDeclaration): KSClassDeclaration? {
            val subEntityOfErasure = resolver.getClassDeclarationByName(SubEntityOf::class.qualifiedName!!)!!.asStarProjectedType()
            return declaration.superTypes
                .map { it.resolve() }
                .filter { (it.declaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE }
                .firstOrNull { it.starProjection() == subEntityOfErasure }
                ?.arguments?.get(0)
                ?.type
                ?.resolve()
                ?.declaration as? KSClassDeclaration?
        }

        private fun parseTableName(declaration: KSClassDeclaration): String {
            val table = declaration.getAnnotationsByType(Table::class).firstOrNull()
            return table?.value ?: declaration.simpleName.asString()
        }

        private fun parseColumns(declaration: KSClassDeclaration, columnsNameConverter: NameConverter?): LinkedHashMap<String, ColumnModel> {
            val constructor = findEntityConstructor(declaration)
            return if (constructor.parameters.isEmpty()) {
                parseMutableEntityColumns(declaration, columnsNameConverter)
            } else {
                parseImmutableEntityColumns(
                    constructor,
                    declaration,
                    columnsNameConverter
                )
            }
        }

        private fun parseMutableEntityColumns(declaration: KSClassDeclaration, columnsNameConverter: NameConverter?): LinkedHashMap<String, ColumnModel> {
            val fields = declaration.getDeclaredProperties().toList()

            val result = LinkedHashMap<String, ColumnModel>()
            for (field in fields) {
                val columnType = field.type.resolve()
                val columnName: String = parseColumnName(field, columnsNameConverter)
                val isId = field.isAnnotationPresent(Id::class)
                result[field.simpleName.toString()] = ColumnModel(field, columnType, columnName, isId)
            }
            return result
        }

        private fun parseImmutableEntityColumns(
            constructor: KSFunctionDeclaration,
            declaration: KSClassDeclaration,
            columnsNameConverter: NameConverter?
        ): LinkedHashMap<String, ColumnModel> {
            val constructorParameters = constructor.parameters
            val result = LinkedHashMap<String, ColumnModel>(constructorParameters.size)
            for (constructorParameter in constructorParameters) {
                val columnType = constructorParameter.type.resolve()
                val columnName: String = parseColumnName(constructorParameter, columnsNameConverter)
                val property = declaration.getDeclaredProperties().first { it.simpleName.asString() == constructorParameter.name!!.asString() }
                // column _must_ be simple type
                val isId = constructorParameter.isAnnotationPresent(Id::class) || property.isAnnotationPresent(Id::class)
                result[property.simpleName.asString()] = ColumnModel(property, columnType, columnName, isId)
            }
            return result
        }
    }


}


 */
