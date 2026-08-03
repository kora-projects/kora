package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.io.BufferedInputStream
import java.nio.charset.Charset
import java.util.regex.Pattern

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
            val result = ArrayList<QueryIndex>()
            val pattern = sqlParameterPattern(sqlParameterName)
            val matcher = pattern.matcher(rawSql)
            while (matcher.find()) {
                val mr = matcher.toMatchResult()
                val start = mr.start(1)
                val end = mr.end()
                result.add(QueryIndex(start, end))
            }

            return QueryParameter(sqlParameterName, methodParameterNumber, result.map { it.start }, result)
        }

        private fun parseEntityDirectParameter(rawSql: String, methodParameterNumber: Int, sqlParameterName: String): QueryParameter {
            val result = ArrayList<QueryIndex>()
            val pattern = sqlParameterPattern(sqlParameterName)
            val matcher = pattern.matcher(rawSql)
            while (matcher.find()) {
                val mr = matcher.toMatchResult()
                val start = mr.start(1)
                val end = mr.end()
                result.add(QueryIndex(start, end))
            }

            return QueryParameter(sqlParameterName, methodParameterNumber, result.map { it.start }, result)
        }

        private fun sqlParameterPattern(sqlParameterName: String): Pattern {
            return Pattern.compile("[\\s\\n,=(\\[](?<param>:" + sqlParameterName + ")(?=[\\s\\n,:)=\\];]|$)");
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

            val matcher = allSqlParameterPattern().matcher(sql)
            while (matcher.find()) {
                val placeholder = matcher.group("param").substring(1)
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

        private fun allSqlParameterPattern(): Pattern {
            return Pattern.compile("(?:^|[\\s\\n,=(\\[<>+\\-*/|&])(?<param>:[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)(?=[\\s\\n,:)=\\];<>+\\-*/|&]|$)")
        }
    }
}
