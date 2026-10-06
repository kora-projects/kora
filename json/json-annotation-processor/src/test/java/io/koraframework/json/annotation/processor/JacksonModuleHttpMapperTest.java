package io.koraframework.json.annotation.processor;

import io.koraframework.common.Either;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class JacksonModuleHttpMapperTest extends AbstractJsonAnnotationProcessorTest {

    private static HttpClientResponse response(int code, HttpHeaders headers, String json) {
        return new HttpClientResponse() {
            @Override
            public int code() {return code;}

            @Override
            public HttpHeaders headers() {return headers;}

            @Override
            public HttpBodyInput body() {return HttpBody.of(json.getBytes(StandardCharsets.UTF_8));}

            @Override
            public void close() {}
        };
    }

    private <T> T mapperFromGraph(String module, @Language("java") String mapperType) throws Exception {
        var res = compile(List.of(new JsonAnnotationProcessor(), new KoraAppProcessor()), """
            public record Dto(String name) {}
            """, """
            public record Err(String error) {}
            """, """
            @KoraApp
            public interface TestApp extends io.koraframework.json.jackson.module.JacksonModule, io.koraframework.json.common.JsonModule, %s {
              default tools.jackson.databind.ObjectMapper objectMapper() { return new tools.jackson.databind.ObjectMapper(); }
              record Holder(Object m) {}
              @Root default Holder root(@Json %s m) { return new Holder(m); }
            }
            """.formatted(module, mapperType));
        res.assertSuccess();
        var graph = loadGraph("TestApp");
        var holderClass = compileResult.loadClass("TestApp$Holder");
        return (T) holderClass.getMethod("m").invoke(graph.findByType(holderClass));
    }

    @Test
    void serverResponseEntityKeepsCodeAndHeaders() throws Exception {
        HttpServerResponseMapper<HttpResponseEntity<Object>> mapper = mapperFromGraph(
            "io.koraframework.http.server.common.response.mapper.HttpServerResponseMapperModule",
            "io.koraframework.http.server.common.response.HttpServerResponseMapper<io.koraframework.http.common.HttpResponseEntity<Dto>>"
        );

        var resp = mapper.apply(Mockito.mock(HttpServerRequest.class), HttpResponseEntity.of(201, HttpHeaders.of("x-test", "val"), newObject("Dto", "x")));
        var bos = new ByteArrayOutputStream();
        resp.body().write(bos);

        assertThat(resp.code()).isEqualTo(201);
        assertThat(resp.headers().getFirst("x-test")).isEqualTo("val");
        assertThat(bos.toString(StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"x\"}");
    }

    @Test
    void clientResponseEntityHasCodeHeadersAndBody() throws Exception {
        HttpClientResponseMapper<HttpResponseEntity<Object>> mapper = mapperFromGraph(
            "io.koraframework.http.client.common.response.HttpClientResponseMapperModule",
            "io.koraframework.http.client.common.response.HttpClientResponseMapper<io.koraframework.http.common.HttpResponseEntity<Dto>>"
        );

        var entity = mapper.apply(response(201, HttpHeaders.of("x-test", "val"), "{\"name\":\"x\"}"));

        assertThat(entity.code()).isEqualTo(201);
        assertThat(entity.headers().getFirst("x-test")).isEqualTo("val");
        assertThat(entity.body()).isEqualTo(newObject("Dto", "x"));
    }

    @Test
    void clientEitherMapsSuccessToLeftAndErrorToRight() throws Exception {
        HttpClientResponseMapper<Either<Object, Object>> mapper = mapperFromGraph(
            "io.koraframework.http.client.common.response.HttpClientResponseMapperModule",
            "io.koraframework.http.client.common.response.HttpClientResponseMapper<io.koraframework.common.Either<Dto, Err>>"
        );

        assertThat(mapper.apply(response(200, HttpHeaders.of(), "{\"name\":\"x\"}"))).isEqualTo(Either.left(newObject("Dto", "x")));
        assertThat(mapper.apply(response(400, HttpHeaders.of(), "{\"error\":\"bad\"}"))).isEqualTo(Either.right(newObject("Err", "bad")));
    }

    @Test
    void clientResponseEntityOfEither() throws Exception {
        HttpClientResponseMapper<HttpResponseEntity<Either<Object, Object>>> mapper = mapperFromGraph(
            "io.koraframework.http.client.common.response.HttpClientResponseMapperModule",
            "io.koraframework.http.client.common.response.HttpClientResponseMapper<io.koraframework.http.common.HttpResponseEntity<io.koraframework.common.Either<Dto, Err>>>"
        );

        var entity = mapper.apply(response(404, HttpHeaders.of(), "{\"error\":\"bad\"}"));

        assertThat(entity.code()).isEqualTo(404);
        assertThat(entity.body()).isEqualTo(Either.right(newObject("Err", "bad")));
    }
}
