package ru.tinkoff.kora.database.symbol.processor

import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.io.BufferedInputStream
import java.nio.charset.Charset

data class QueryWithParameters(val rawQuery: String, val parameters: List<QueryParameter>) {
    data class QueryParameter(val sqlParameterName: String, val methodIndex: Int, val sqlIndexes: List<Int>)

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

        fun parse(rq: String, parameters: List<ru.tinkoff.kora.database.symbol.processor.model.QueryParameter>): QueryWithParameters {
            val params = mutableListOf<QueryParameter>()
            var rawSql = rq
            if (rawSql.startsWith("classpath:/")) {
                val file = ClassLoader.getSystemClassLoader().getResource(rawSql.replaceFirst("classpath:/", ""))
                val content = file.content as BufferedInputStream
                rawSql = content.use {
                    it.readAllBytes().toString(Charset.defaultCharset())
                }
            }
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
                    for (field in parameter.entity.fields) {
                        parseSimpleParameter(rawSql, i, parameterName + "." + field.property.simpleName.getShortName()).let {
                            if (it.sqlIndexes.isNotEmpty()) {
                                params.add(it)
                            }
                        }
                    }
                }
                if (params.size == size) {
                    throw ProcessingErrorException("Parameter usage was not found in sql: ${parameter.name}", parameter.variable)
                }
            }
            val paramsNumbers = params.asSequence()
                .map { it.sqlIndexes }
                .flatten()
                .sorted()
            val processedParams = params
                .map { p: QueryParameter ->
                    QueryParameter(
                        p.sqlParameterName, p.methodIndex, p.sqlIndexes
                            .map { paramsNumbers.indexOf(it) }
                    )
                }

            return QueryWithParameters(rawSql, processedParams)
        }

        private fun parseSimpleParameter(rawSql: String, methodParameterNumber: Int, sqlParameterName: String): QueryParameter {
            var index = -1
            val result = ArrayList<Int>()
            while (rawSql.indexOf(":$sqlParameterName", index + 1).also { index = it } >= 0) {
                val indexAfter = index + sqlParameterName.length + 1
                if (rawSql.length >= indexAfter + 1) {
                    val charAfter = rawSql[indexAfter]
                    if (Character.isAlphabetic(charAfter.code) || charAfter == '_' || charAfter == '$' || Character.isDigit(charAfter)) {
                        continue
                    }
                }
                result.add(index)
            }
            return QueryParameter(sqlParameterName, methodParameterNumber, result)
        }
    }
}
