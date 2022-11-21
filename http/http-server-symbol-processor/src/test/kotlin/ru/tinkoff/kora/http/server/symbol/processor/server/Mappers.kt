package ru.tinkoff.kora.http.server.symbol.processor.server

import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.util.ReactorUtils
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse
import ru.tinkoff.kora.http.server.common.handler.*
import ru.tinkoff.kora.http.server.symbol.processor.controllers.ReadableEntity
import ru.tinkoff.kora.http.server.symbol.processor.controllers.SomeEntity
import ru.tinkoff.kora.http.server.symbol.processor.controllers.TestEnum
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ForkJoinPool

object Mappers {
    fun lookupParameter(type: Type): Tuple2<Class<*>, Any> {
        if (type is Class<*>) {
            if (BlockingRequestExecutor::class.java.isAssignableFrom(type)) {
                return Tuples.of(BlockingRequestExecutor::class.java, BlockingRequestExecutor.Default(ForkJoinPool.commonPool()))
            }
            try {
                return Tuples.of(type, type.getConstructor().newInstance())
            } catch (e: Exception) {
                when (e) {
                    is IllegalAccessException, is InstantiationException, is InvocationTargetException, is NoSuchMethodException -> throw RuntimeException(e)
                    else -> throw e
                }
            }
        }
        val parameter = type as ParameterizedType
        val rawType = parameter.rawType
        if (rawType is Class<*> && HttpServerResponseEntityMapper::class.java.isAssignableFrom(rawType)) {
            return Tuples.of(HttpServerResponseEntityMapper::class.java, HttpServerResponseEntityMapper(lookupResponseMapper(parameter)))
        }
        if (rawType is Class<*> && rawType.isAssignableFrom(HttpServerRequestMapper::class.java)) {
            return Tuples.of(HttpServerRequestMapper::class.java, lookupRequestMapper(parameter))
        }
        if (rawType is Class<*> && rawType.isAssignableFrom(HttpServerResponseMapper::class.java)) {
            return Tuples.of(HttpServerResponseMapper::class.java, lookupResponseMapper(parameter))
        }
        if (rawType is Class<*> && rawType.isAssignableFrom(StringParameterReader::class.java)) {
            return Tuples.of(StringParameterReader::class.java, lookupStringReader(parameter))
        }
        throw IllegalStateException()
    }


    fun lookupRequestMapper(type: Type): HttpServerRequestMapper<*> {
        val parameter = type as ParameterizedType
        val requestType = parameter.actualTypeArguments[0]
        if (requestType == HttpServerRequest::class.java) {
            return noopRequestMapper()
        }
        if (requestType == String::class.java) {
            return stringRequestMapper()
        }
        if (requestType == SomeEntity::class.java) {
            return jsonRequestMapper<SomeEntity>(SomeEntity::class.java)
        }
        if (requestType == ByteArray::class.java) {
            return byteArrayRequestMapper()
        }
        if (requestType == Integer::class.java) {
            return integerRequestMapper()
        }
        var typeRef = TypeRef.of(
            List::class.java,
            TypeRef.of(
                List::class.java,
                TypeRef.of(
                    Tuple2::class.java,
                    TypeRef.of(
                        List::class.java,
                        TypeRef.of(String::class.java)
                    ),
                    TypeRef.of(
                        List::class.java,
                        TypeRef.of(SomeEntity::class.java)
                    )
                )
            )
        ) as TypeRef<*>
        if (requestType == typeRef) {
            return jsonRequestMapper<TypeRef<*>>(typeRef)
        }
        typeRef = TypeRef.of(
            Publisher::class.java, TypeRef.of(
                ByteBuffer::class.java
            )
        )
        if (requestType == typeRef) {
            return byteBufferPublisherRequestMapper()
        }
        throw RuntimeException("Unknown test request mapper: $requestType")
    }

    fun lookupResponseMapper(type: Type): HttpServerResponseMapper<*> {
        val parameter = type as ParameterizedType
        val responseType = parameter.actualTypeArguments[0]
        if (responseType == Unit::class.java) {
            return voidResponseMapper()
        }
        if (responseType == String::class.java) {
            return stringResponseMapper()
        }
        if (responseType == Integer::class.java) {
            return integerResponseMapper()
        }
        if (responseType == HttpServerResponse::class.java || responseType == SimpleHttpServerResponse::class.java) {
            return noopResponseMapper()
        }
        if (responseType == SomeEntity::class.java) {
            return jsonResponseMapper(TypeRef.of(SomeEntity::class.java))
        }
        if (responseType == TypeRef.of(
                HttpServerResponseEntity::class.java, TypeRef.of(
                    String::class.java
                )
            )
        ) {
            return HttpServerResponseEntityMapper(stringResponseMapper())
        }
        if (responseType == TypeRef.of(
                HttpServerResponseEntity::class.java, TypeRef.of(
                    ByteArray::class.java
                )
            )
        ) {
            return HttpServerResponseEntityMapper(stringResponseMapper())
        }
        throw RuntimeException("Unknown test response mapper: $responseType")
    }

    private fun <T> jsonRequestMapper(someEntityClass: Type): HttpServerRequestMapper<T> {
        return HttpServerRequestMapper<T> { Mono.just(null) }
    }


    private fun <T> jsonResponseMapper(someEntityClass: TypeRef<T>): HttpServerResponseMapper<T> {
        return HttpServerResponseMapper<T> { result ->
            Mono.just(
                SimpleHttpServerResponse(
                    200,
                    "text/plain",
                    HttpHeaders.EMPTY,
                    ByteBuffer.wrap(result.toString().toByteArray(StandardCharsets.UTF_8))
                )
            )
        }

    }

    private fun noopResponseMapper(): HttpServerResponseMapper<HttpServerResponse?> {
        return HttpServerResponseMapper { data: HttpServerResponse? -> Mono.just(data) }
    }

    private fun noopRequestMapper(): HttpServerRequestMapper<HttpServerRequest> {
        return HttpServerRequestMapper { data: HttpServerRequest? -> Mono.just(data) }
    }


    private fun stringRequestMapper(): HttpServerRequestMapper<String> {
        return HttpServerRequestMapper { r: HttpServerRequest ->
            Mono.from(r.body()).map { bb: ByteBuffer? ->
                StandardCharsets.UTF_8.decode(
                    bb
                )
            }.map { obj: CharBuffer -> obj.toString() }
        }
    }

    private fun voidResponseMapper(): HttpServerResponseMapper<Unit?> {
        return HttpServerResponseMapper { result: Unit? ->
            Mono.just(
                SimpleHttpServerResponse(
                    200,
                    "text/plain",
                    HttpHeaders.of(),
                    null
                )
            )
        }
    }

    private fun stringResponseMapper(): HttpServerResponseMapper<String?> {
        return HttpServerResponseMapper { result: String? ->
            Mono.just(
                SimpleHttpServerResponse(
                    200,
                    "text/plain",
                    HttpHeaders.of(),
                    StandardCharsets.UTF_8.encode(result ?: "null")
                )
            )
        }
    }

    private fun integerRequestMapper(): HttpServerRequestMapper<Int> {
        return HttpServerRequestMapper { r: HttpServerRequest? ->
            Mono.from<String>(stringRequestMapper().apply(r)).map { s: String -> s.toInt() }
        }
    }

    private fun integerResponseMapper(): HttpServerResponseMapper<Int> {
        return HttpServerResponseMapper { r: Int ->
            Mono.just(
                SimpleHttpServerResponse(
                    200,
                    "text/plain",
                    HttpHeaders.of(),
                    StandardCharsets.UTF_8.encode(r.toString())
                )
            )
        }
    }

    private fun byteBufferPublisherRequestMapper(): HttpServerRequestMapper<ByteBuffer> {
        return HttpServerRequestMapper { r: HttpServerRequest -> ReactorUtils.toByteBufferMono(r.body()) }
    }

    private fun byteArrayRequestMapper(): HttpServerRequestMapper<ByteArray> {
        return HttpServerRequestMapper { request: HttpServerRequest -> ReactorUtils.toByteArrayMono(request.body()) }
    }

    private fun readableEntityStringReader(): StringParameterReader<ReadableEntity> {
        return StringParameterReader { string: String? ->
            string?.let { ReadableEntity(it) }
        }
    }

    private fun lookupStringReader(type: Type): StringParameterReader<*> {
        val parameter = type as ParameterizedType
        val requestType = parameter.actualTypeArguments[0]
        if (requestType == ReadableEntity::class.java) {
            return readableEntityStringReader()
        }
        if (requestType == Integer::class.java) {
            return StringParameterReader { it?.toIntOrNull() ?: throw Exception("Cannot convert $it to int") }
        }
        if (requestType == TestEnum::class.java) {
            return EnumStringParameterReader(TestEnum.values(), TestEnum::name)
        }
        throw RuntimeException("Unknown test string parameter reader: $requestType")
    }
}
