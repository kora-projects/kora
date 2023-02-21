package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper
import ru.tinkoff.kora.http.common.annotation.Header
import ru.tinkoff.kora.http.common.annotation.Path
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.parseMappingData

interface Parameter {
    data class HeaderParameter(val parameter: KSValueParameter, val headerName: String) : Parameter

    data class QueryParameter(val parameter: KSValueParameter, val queryParameterName: String) : Parameter

    data class PathParameter(val parameter: KSValueParameter, val pathParameterName: String) : Parameter

    data class BodyParameter(val parameter: KSValueParameter, val mapper: MappingData?) : Parameter

    class ParameterParser(private val resolver: Resolver) {
        private val requestMapperType: KSType? = resolver
            .getClassDeclarationByName(HttpClientRequestMapper::class.qualifiedName!!)?.asStarProjectedType()

        @KspExperimental
        fun parseParameter(method: KSFunctionDeclaration, parameterIndex: Int): Parameter {
            val parameter = method.parameters[parameterIndex]
            val header = parameter.getAnnotationsByType(Header::class).firstOrNull()
            val path = parameter.getAnnotationsByType(Path::class).firstOrNull()
            val query = parameter.getAnnotationsByType(Query::class).firstOrNull()
            if (header != null) {
                val name = header.value.ifEmpty { parameter.name!!.asString() }
                return HeaderParameter(parameter, name)
            }
            if (path != null) {
                val name = path.value.ifEmpty { parameter.name!!.asString() }
                return PathParameter(parameter, name)
            }
            if (query != null) {
                val name = query.value.ifEmpty { parameter.name!!.asString() }
                return QueryParameter(parameter, name)
            }
            val mapping = parameter.parseMappingData().getMapping(requestMapperType!!)
            return BodyParameter(parameter, mapping)
        }
    }
}
