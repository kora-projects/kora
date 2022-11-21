package ru.tinkoff.kora.http.server.symbol.processor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.http.server.common.jackson.JacksonHttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.jackson.JacksonHttpServerResponseMapper;
import ru.tinkoff.kora.http.server.ksp.processor.controllers.SomeEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class Mappers {
    public static Tuple2<Class<?>, Object> lookupParameter(Type type) {
        if (type instanceof Class clazz) {
            if (BlockingRequestExecutor.class.isAssignableFrom(clazz)) {
                return Tuples.of(BlockingRequestExecutor.class, new BlockingRequestExecutor.Default(ForkJoinPool.commonPool()));
            }
            try {
                return Tuples.of(clazz, clazz.getConstructor().newInstance());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        var parameter = (ParameterizedType) type;
        if (parameter.getRawType() instanceof Class<?> clazz && HttpServerResponseEntityMapper.class.isAssignableFrom(clazz)) {
            return Tuples.of(HttpServerResponseEntityMapper.class, new HttpServerResponseEntityMapper<>(lookupResponseMapper(parameter)));
        }
        if (parameter.getRawType() instanceof Class clazz && HttpServerRequestMapper.class.isAssignableFrom(clazz)) {
            return Tuples.of(HttpServerRequestMapper.class, lookupRequestMapper(parameter));
        }
        if (parameter.getRawType() instanceof Class clazz && HttpServerResponseMapper.class.isAssignableFrom(clazz)) {
            return Tuples.of(HttpServerResponseMapper.class, lookupResponseMapper(parameter));
        }
        throw new IllegalStateException();
    }


    public static HttpServerRequestMapper<?> lookupRequestMapper(Type type) {
        var parameter = (ParameterizedType) type;
        var requestType = parameter.getActualTypeArguments()[0];
        if (requestType.equals(HttpServerRequest.class)) {
            return noopRequestMapper();
        }
        if (requestType.equals(String.class)) {
            return stringRequestMapper();
        }
        if (requestType.equals(SomeEntity.class)) {
            return jsonRequestMapper(SomeEntity.class);
        }
        if (requestType.equals(byte[].class)) {
            return byteArrayRequestMapper();
        }
        if (requestType.equals(Integer.class)) {
            return integerRequestMapper();
        }
        var typeRef = (TypeRef<?>) TypeRef.of(
            List.class,
            TypeRef.of(
                List.class,
                TypeRef.of(
                    Tuple2.class,
                    TypeRef.of(
                        List.class,
                        TypeRef.of(String.class)
                    ),
                    TypeRef.of(
                        List.class,
                        TypeRef.of(SomeEntity.class)
                    )
                )
            )
        );
        if (requestType.equals(typeRef)) {
            return jsonRequestMapper(typeRef);
        }
        typeRef = TypeRef.of(Publisher.class, TypeRef.of(ByteBuffer.class));
        if (requestType.equals(typeRef)) {
            return byteBufferPublisherRequestMapper();
        }

        throw new RuntimeException("Unknown test request mapper: " + requestType);
    }


    public static HttpServerResponseMapper<?> lookupResponseMapper(Type type) {
        var parameter = (ParameterizedType) type;
        var responseType = parameter.getActualTypeArguments()[0];
        if (responseType.equals(Void.class)) {
            return voidResponseMapper();
        }
        if (responseType.equals(String.class)) {
            return stringResponseMapper();
        }
        if (responseType.equals(Integer.class)) {
            return integerResponseMapper();
        }
        if (responseType.equals(HttpServerResponse.class)) {
            return noopResponseMapper();
        }

        if (responseType.equals(SomeEntity.class)) {
            return jsonResponseMapper(TypeRef.of(SomeEntity.class));
        }
        if (responseType.equals(TypeRef.of(HttpServerResponseEntity.class, TypeRef.of(String.class)))) {
            return new HttpServerResponseEntityMapper<>(stringResponseMapper());
        }

        throw new RuntimeException("Unknown test response mapper: " + responseType);
    }

    private static <T> HttpServerRequestMapper<T> jsonRequestMapper(Type someEntityClass) {
        return new JacksonHttpServerRequestMapper<>(new ObjectMapper().findAndRegisterModules(), someEntityClass);
    }


    private static <T> HttpServerResponseMapper<T> jsonResponseMapper(TypeRef<T> someEntityClass) {
        return new JacksonHttpServerResponseMapper<>(new ObjectMapper().findAndRegisterModules(), someEntityClass);
    }

    private static HttpServerResponseMapper<HttpServerResponse> noopResponseMapper() {
        return Mono::just;
    }

    private static HttpServerRequestMapper<HttpServerRequest> noopRequestMapper() {
        return Mono::just;
    }

    private static HttpServerRequestMapper<String> stringRequestMapper() {
        return r -> Mono.from(r.body()).map(StandardCharsets.UTF_8::decode).map(CharBuffer::toString);
    }

    private static HttpServerResponseMapper<Void> voidResponseMapper() {
        return result -> Mono.just(new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), null));
    }

    private static HttpServerResponseMapper<String> stringResponseMapper() {
        return result -> Mono.just(new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode(result != null ? result : "null")));
    }

    private static HttpServerRequestMapper<Integer> integerRequestMapper() {
        return r -> Mono.from(stringRequestMapper().apply(r)).map(Integer::parseInt);
    }

    private static HttpServerResponseMapper<Integer> integerResponseMapper() {
        return r -> Mono.just(new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode(r.toString())));
    }

    private static HttpServerRequestMapper<ByteBuffer> byteBufferPublisherRequestMapper() {
        return r -> ReactorUtils.toByteBufferMono(r.body());
    }

    private static HttpServerRequestMapper<byte[]> byteArrayRequestMapper() {
        return request -> ReactorUtils.toByteArrayMono(request.body());
    }


}
