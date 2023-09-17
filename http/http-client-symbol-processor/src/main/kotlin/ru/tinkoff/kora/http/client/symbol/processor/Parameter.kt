package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.header
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientRequestMapper
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.path
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.query
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.parseMappingData

interface Parameter {
    data class HeaderParameter(val parameter: KSValueParameter, val headerName: String) : Parameter

    data class QueryParameter(val parameter: KSValueParameter, val queryParameterName: String) : Parameter

    data class PathParameter(val parameter: KSValueParameter, val pathParameterName: String) : Parameter

    data class BodyParameter(val parameter: KSValueParameter, val mapper: MappingData?) : Parameter

    companion object {
        fun parseParameter(method: KSFunctionDeclaration, parameterIndex: Int): Parameter {
            val parameter = method.parameters[parameterIndex]
            val header = parameter.findAnnotation(header)
            val path = parameter.findAnnotation(path)
            val query = parameter.findAnnotation(query)
            val parameterName = parameter.name!!.asString()
            if (header != null) {
                val name = header.findValueNoDefault<String>("value").orEmpty().ifEmpty { parameterName }
                return HeaderParameter(parameter, name)
            }
            if (path != null) {
                val name = path.findValueNoDefault<String>("value").orEmpty().ifEmpty { parameterName }
                return PathParameter(parameter, name)
            }
            if (query != null) {
                val name = query.findValueNoDefault<String>("value").orEmpty().ifEmpty { parameterName }
                return QueryParameter(parameter, name)
            }
            val mapping = parameter.parseMappingData().getMapping(httpClientRequestMapper)
            return BodyParameter(parameter, mapping)
        }
    }
}
