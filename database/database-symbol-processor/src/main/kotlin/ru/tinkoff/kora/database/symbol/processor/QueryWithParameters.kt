package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
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

        fun parse(rq: String, parameters: List<ru.tinkoff.kora.database.symbol.processor.model.QueryParameter>, method: KSFunctionDeclaration): QueryWithParameters {
            val params = mutableListOf<QueryParameter>()
            var rawSql = rq
            if (rawSql.startsWith("classpath:/")) {
                val file = ClassLoader.getSystemClassLoader().getResource(rawSql.replaceFirst("classpath:/", ""))
                val content = file.content as BufferedInputStream
                rawSql = content.use {
                    it.readAllBytes().toString(Charset.defaultCharset())
                }
            }

            val parser = QueryMacrosParser()
            rawSql = parser.parse(rawSql, method)

            parameters.forEachIndexed { i, _parameter ->
                var parameter = _parameter
                val parameterName = parameter.name
                if (parameter is ru.tinkoff.kora.database.symbol.processor.model.QueryParameter.ConnectionParameter) {
                    return@forEachIndexed
                }
                val size = params.size
                if (parameter is ru.tinkoff.kora.database.symbol.processor.model.QueryParameter.BatchParameter) {
                    parameter = parameter.parameter
                }
                if (parameter is ru.tinkoff.kora.database.symbol.processor.model.QueryParameter.SimpleParameter) {
                    parseSimpleParameter(rawSql, i, parameterName).let {
                        if (it.sqlIndexes.isNotEmpty()) {
                            params.add(it)
                        }
                    }
                }
                if (parameter is ru.tinkoff.kora.database.symbol.processor.model.QueryParameter.EntityParameter) {
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
                        "Parameter usage wasn't found in sql: ${parameter.name}",
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
    }
}
