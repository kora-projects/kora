package io.koraframework.http.client.symbol.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.cookie
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.header
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.httpClientRequestMapper
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.path
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.query
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.MappingData
import io.koraframework.ksp.common.parseMappingData

interface Parameter {
    data class HeaderParameter(val parameter: KSValueParameter, val headerName: String) : Parameter

    data class QueryParameter(val parameter: KSValueParameter, val queryParameterName: String) : Parameter

    data class PathParameter(val parameter: KSValueParameter, val pathParameterName: String) : Parameter

    data class CookieParameter(val parameter: KSValueParameter, val name: String) : Parameter

    data class BodyParameter(val parameter: KSValueParameter, val mapper: MappingData?) : Parameter

    companion object {
        fun parseParameter(method: KSFunctionDeclaration, parameterIndex: Int): Parameter {
            val parameter = method.parameters[parameterIndex]
            val header = parameter.findAnnotation(header)
            val path = parameter.findAnnotation(path)
            val query = parameter.findAnnotation(query)
            val cookie = parameter.findAnnotation(cookie)
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
            if (cookie != null) {
                val name = cookie.findValueNoDefault<String>("value").orEmpty().ifEmpty { parameterName }
                return CookieParameter(parameter, name)
            }
            val mapping = parameter.parseMappingData().getMapping(httpClientRequestMapper)
            return BodyParameter(parameter, mapping)
        }
    }
}
