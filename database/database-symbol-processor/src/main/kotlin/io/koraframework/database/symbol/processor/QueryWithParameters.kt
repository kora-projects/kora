package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.io.BufferedInputStream
import java.nio.charset.Charset

data class QueryWithParameters(val rawQuery: String, val parameters: List<QueryParameter>) {

    data class QueryParameter(val sqlParameterName: String, val methodIndex: Int, val sqlIndexes: List<Int>, val queryIndexes: List<QueryIndex>)

    data class QueryIndex(val start: Int, val end: Int)

    fun find(name: String): QueryParameter? {
        for (parameter in parameters) {
            if (parameter.sqlParameterName == name) {
                return parameter
            }
        }
        return null
    }

    fun find(methodIndex: Int): QueryParameter? {
        for (parameter in parameters) {
            if (parameter.methodIndex == methodIndex) {
                return parameter
            }
        }
        return null
    }

    companion object {

        fun parse(
            rq: String,
            parameters: List<io.koraframework.database.symbol.processor.model.QueryParameter>,
            method: KSFunctionDeclaration,
            methodType: KSFunction,
            repositoryType: KSType
        ): QueryWithParameters {
            val params = mutableListOf<QueryParameter>()
            var rawSql = rq
            if (rawSql.startsWith("classpath:/")) {
                val resourcePath = rawSql.replaceFirst("classpath:/", "")
                val file = ClassLoader.getSystemClassLoader().getResource(resourcePath)
                    ?: throw ProcessingErrorException(
                        """
                        SQL query resource wasn't found:
                          $rawSql

                        Problem:
                          Query starts with classpath:/ but the resource can't be read from the compilation classpath.

                        Hint:
                          Resource path is resolved relative to classpath roots after the classpath:/ prefix.

                        Fix:
                          Check that the SQL file exists, the path is correct, and the resource is included in the module sources/resources.
                        """.trimIndent(),
                        method
                    )
                val content = file.content as BufferedInputStream
                rawSql = content.use {
                    it.readAllBytes().toString(Charset.defaultCharset())
                }
            }
            rawSql = rawSql.trim()

            val parser = QueryMacrosParser()
            rawSql = parser.parse(rawSql, method, methodType, repositoryType)
            validateSqlPlaceholders(rawSql, parameters, method)

            parameters.forEachIndexed { i, _parameter ->
                var parameter = _parameter
                val parameterName = parameter.name
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.ConnectionParameter) {
                    return@forEachIndexed
                }
                val size = params.size
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.BatchParameter) {
                    parameter = parameter.parameter
                }
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.SimpleParameter) {
                    parseSimpleParameter(rawSql, i, parameterName).let {
                        if (it.sqlIndexes.isNotEmpty()) {
                            params.add(it)
                        }
                    }
                }
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.EntityParameter) {
                    for (field in parameter.entity.columns) {
                        parseSimpleParameter(rawSql, i, field.queryParameterName(parameterName)).let {
                            if (it.sqlIndexes.isNotEmpty()) {
                                params.add(it)
                            }
                        }
                    }
                    parseEntityDirectParameter(rawSql, i, parameterName).let {
                        if (it.sqlIndexes.isNotEmpty()) {
                            params.add(it)
                        }
                    }
                }
                if (params.size == size) {
                    throw ProcessingErrorException(
                        """
                        Query parameter is unused:
                          ${parameter.name}

                        Problem:
                          Method parameter wasn't found in SQL query placeholders.

                        Hint:
                          Parameters are matched by ':name'. Entity parameters are matched by their field placeholders, for example ':entity.field'.

                        Fix:
                          Add ':${parameter.name}' to the query, rename the method parameter to match the SQL placeholder, or remove the unused parameter.
                        """.trimIndent(),
                        parameter.variable
                    )
                }
            }

            val paramsNumbers = params.asSequence()
                .map { it.sqlIndexes }
                .flatten()
                .sorted()

            val processedParams = params
                .map { p ->
                    QueryParameter(
                        p.sqlParameterName,
                        p.methodIndex,
                        p.sqlIndexes.map { paramsNumbers.indexOf(it) },
                        p.queryIndexes
                    )
                }

            return QueryWithParameters(rawSql, processedParams)
        }

        private fun parseSimpleParameter(rawSql: String, methodParameterNumber: Int, sqlParameterName: String): QueryParameter {
            val result = findSqlParameterIndexes(rawSql, sqlParameterName)

            return QueryParameter(sqlParameterName, methodParameterNumber, result.map { it.start }, result)
        }

        private fun parseEntityDirectParameter(rawSql: String, methodParameterNumber: Int, sqlParameterName: String): QueryParameter {
            val result = findSqlParameterIndexes(rawSql, sqlParameterName)

            return QueryParameter(sqlParameterName, methodParameterNumber, result.map { it.start }, result)
        }

        private fun validateSqlPlaceholders(
            sql: String,
            parameters: List<io.koraframework.database.symbol.processor.model.QueryParameter>,
            method: KSFunctionDeclaration
        ) {
            val availableParameters = linkedMapOf<String, io.koraframework.database.symbol.processor.model.QueryParameter>()
            val availableEntityFields = linkedMapOf<String, List<String>>()
            for (_parameter in parameters) {
                var parameter = _parameter
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.ConnectionParameter) {
                    continue
                }
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.BatchParameter) {
                    parameter = parameter.parameter
                }
                availableParameters[parameter.name] = parameter
                if (parameter is io.koraframework.database.symbol.processor.model.QueryParameter.EntityParameter) {
                    val fields = parameter.entity.columns.map { it.queryParameterName(parameter.name) }
                    fields.forEach { availableParameters[it] = parameter }
                    availableEntityFields[parameter.name] = fields
                }
            }

            for (sqlParameter in findSqlParameters(sql)) {
                val placeholder = sqlParameter.name
                if (availableParameters.containsKey(placeholder)) {
                    continue
                }
                val dotIndex = placeholder.indexOf('.')
                if (dotIndex > 0) {
                    val rootName = placeholder.substring(0, dotIndex)
                    val entityFields = availableEntityFields[rootName]
                    if (entityFields != null) {
                        throw ProcessingErrorException(
                            """
                            SQL query placeholder has no matching entity field:
                              :$placeholder

                            Problem:
                              Query contains ':$placeholder', but parameter '$rootName' has no mapped field with this name.

                            Available fields for parameter '$rootName':
                            ${formatAvailable(entityFields)}

                            Hint:
                              Entity fields are matched as ':entity.field'. Embedded fields use the same dotted form.

                            Fix:
                              Rename ':$placeholder' to one of the available fields, or add the missing field to the entity.
                            """.trimIndent(),
                            method
                        )
                    }
                }
                throw ProcessingErrorException(
                    """
                    SQL query placeholder has no matching method parameter:
                      :$placeholder

                    Problem:
                      Query contains ':$placeholder', but repository method has no parameter or entity field with this name.

                    Available parameters:
                    ${formatAvailable(availableParameters.keys)}

                    Hint:
                      Parameters are matched by ':name'. Entity fields are matched as ':entity.field'.

                    Fix:
                      Rename ':$placeholder' to one of the available parameters, add a method parameter named '$placeholder', or use the correct entity field placeholder.
                    """.trimIndent(),
                    method
                )
            }
        }

        private fun formatAvailable(names: Collection<String>): String {
            if (names.isEmpty()) {
                return "  - <none>"
            }
            return names.joinToString("\n") { "  - :$it" }
        }

        private fun findSqlParameterIndexes(sql: String, sqlParameterName: String): List<QueryIndex> {
            return findSqlParameters(sql)
                .filter { it.name == sqlParameterName }
                .map { QueryIndex(it.start, it.end) }
        }

        private fun findSqlParameters(sql: String): List<SqlParameter> {
            val result = ArrayList<SqlParameter>()
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                if (c == '\'') {
                    i = skipSingleQuoted(sql, i) + 1
                    continue
                }
                if (c == '"') {
                    i = skipDoubleQuoted(sql, i) + 1
                    continue
                }
                if (c == '`') {
                    i = skipUntil(sql, i, '`') + 1
                    continue
                }
                if (c == '[') {
                    i = skipUntil(sql, i, ']') + 1
                    continue
                }
                if (c == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                    i = skipLineComment(sql, i) + 1
                    continue
                }
                if (c == '/' && i + 1 < sql.length && sql[i + 1] == '*') {
                    i = skipBlockComment(sql, i) + 1
                    continue
                }
                if (c == '$') {
                    val dollarQuoteEnd = skipDollarQuoted(sql, i)
                    if (dollarQuoteEnd > i) {
                        i = dollarQuoteEnd + 1
                        continue
                    }
                }
                if (c != ':') {
                    i++
                    continue
                }
                if (i + 1 < sql.length && sql[i + 1] == ':' || i > 0 && sql[i - 1] == ':') {
                    i++
                    continue
                }
                val nameStart = i + 1
                if (nameStart >= sql.length || !isNameStart(sql[nameStart])) {
                    i++
                    continue
                }
                var nameEnd = nameStart + 1
                while (nameEnd < sql.length) {
                    val current = sql[nameEnd]
                    if (isNamePart(current)) {
                        nameEnd++
                        continue
                    }
                    if (current == '.' && nameEnd + 1 < sql.length && isNameStart(sql[nameEnd + 1])) {
                        nameEnd += 2
                        while (nameEnd < sql.length && isNamePart(sql[nameEnd])) {
                            nameEnd++
                        }
                        continue
                    }
                    break
                }
                result.add(SqlParameter(sql.substring(nameStart, nameEnd), i, nameEnd))
                i = nameEnd
            }
            return result
        }

        private fun skipSingleQuoted(sql: String, start: Int): Int {
            var i = start + 1
            while (i < sql.length) {
                if (sql[i] == '\'') {
                    if (i + 1 < sql.length && sql[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    return i
                }
                i++
            }
            return sql.length - 1
        }

        private fun skipDoubleQuoted(sql: String, start: Int): Int {
            var i = start + 1
            while (i < sql.length) {
                if (sql[i] == '"') {
                    if (i + 1 < sql.length && sql[i + 1] == '"') {
                        i += 2
                        continue
                    }
                    return i
                }
                i++
            }
            return sql.length - 1
        }

        private fun skipUntil(sql: String, start: Int, end: Char): Int {
            for (i in start + 1 until sql.length) {
                if (sql[i] == end) {
                    return i
                }
            }
            return sql.length - 1
        }

        private fun skipLineComment(sql: String, start: Int): Int {
            for (i in start + 2 until sql.length) {
                val c = sql[i]
                if (c == '\n' || c == '\r') {
                    return i
                }
            }
            return sql.length - 1
        }

        private fun skipBlockComment(sql: String, start: Int): Int {
            var i = start + 2
            while (i + 1 < sql.length) {
                if (sql[i] == '*' && sql[i + 1] == '/') {
                    return i + 1
                }
                i++
            }
            return sql.length - 1
        }

        private fun skipDollarQuoted(sql: String, start: Int): Int {
            var tagEnd = start + 1
            while (tagEnd < sql.length) {
                val c = sql[tagEnd]
                if (c == '$') {
                    break
                }
                if (!isNamePart(c)) {
                    return start
                }
                tagEnd++
            }
            if (tagEnd >= sql.length || sql[tagEnd] != '$') {
                return start
            }
            val tag = sql.substring(start, tagEnd + 1)
            val end = sql.indexOf(tag, tagEnd + 1)
            return if (end < 0) sql.length - 1 else end + tag.length - 1
        }

        private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_'

        private fun isNamePart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    }

    private data class SqlParameter(val name: String, val start: Int, val end: Int)
}
