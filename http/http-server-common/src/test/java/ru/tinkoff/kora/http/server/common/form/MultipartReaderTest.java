package ru.tinkoff.kora.http.server.common.form;

import org.assertj.core.data.Index;
import org.assertj.core.presentation.Representation;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartReaderTest {
    @RepeatedTest(100)
    void test() {
        var e = """
            --boundary\r
            Content-Disposition: form-data; name="field1"\r
            Content-Type: text/plain\r
            \r
            value1\r
            --boundary\r
            Content-Disposition: form-data; name="field2"; filename="example.txt"\r
            Content-Type: text/plain\r
            \r
            value2\r
            --boundary--\r
            \r""".getBytes(StandardCharsets.UTF_8);
        var f = Flux.<ByteBuffer>create(sink -> {
            var i = 0;
            while (i < e.length) {
                var len = Math.min(ThreadLocalRandom.current().nextInt(10), e.length - i);
                var buf = ByteBuffer.wrap(e, i, len);
                sink.next(buf);
                i += len;
            }
            sink.complete();
        });

        var request = new SimpleHttpServerRequest("POST", "/", f, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=\"boundary\"")
        }, Map.of());
        var result = MultipartReader.read(request)
            .collectList()
            .block();

        assertThat(result)
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field1");
                assertThat(part.fileName()).isNull();
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value1");
            }, Index.atIndex(0))
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field2");
                assertThat(part.fileName()).isEqualTo("example.txt");
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value2");
            }, Index.atIndex(1));
    }

    @RepeatedTest(100)
    void lastBytesErrorTest() {
        var e = """
            --boundary\r
            Content-Disposition: form-data; name="field1"\r
            Content-Type: text/plain\r
            \r
            value1\r
            --boundary\r
            Content-Disposition: form-data; name="field2"; filename="example.txt"\r
            Content-Type: text/plain\r
            \r
            value2\r
            --boundary--\r
            \r
            \r
            \r""".getBytes(StandardCharsets.UTF_8);
        AtomicInteger integer = new AtomicInteger(0);
        var firstFlux = Flux.<ByteBuffer>create(sink -> {
            var i = 0;
            while (i < e.length - 4) {
                var len = Math.min(ThreadLocalRandom.current().nextInt(10), e.length - i - 4);
                var buf = ByteBuffer.wrap(e, i, len);
                sink.next(buf);
                i += len;
            }
            sink.complete();
            integer.addAndGet(i);
        });
        var secondFlux = Flux.<ByteBuffer>create(sink -> {
            var i = integer.get();
            while (i < e.length) {
                var len = Math.min(ThreadLocalRandom.current().nextInt(10), e.length - i);
                var buf = ByteBuffer.wrap(e, i, len);
                sink.next(buf);
                i += len;
            }
            sink.complete();
        });

        var f = firstFlux.concatWith(secondFlux);

        var request = new SimpleHttpServerRequest("POST", "/", f, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=\"boundary\"")
        }, Map.of());
        var result = MultipartReader.read(request)
            .collectList()
            .block();

        assertThat(result)
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field1");
                assertThat(part.fileName()).isNull();
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value1");
            }, Index.atIndex(0))
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field2");
                assertThat(part.fileName()).isEqualTo("example.txt");
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value2");
            }, Index.atIndex(1));
    }

    @Test
    void insomniaMultipart() {
        var body = """
            --X-INSOMNIA-BOUNDARY\r
            Content-Disposition: form-data; name="field1"\r
            Content-Type: text/plain\r
            \r
            value1\r
            --X-INSOMNIA-BOUNDARY\r
            Content-Disposition: form-data; name="field2"; filename="example.txt"\r
            Content-Type: text/plain\r
            \r
            value2\r
            --X-INSOMNIA-BOUNDARY--\r
            \r""".getBytes(StandardCharsets.UTF_8);
        var f = Flux.<ByteBuffer>create(sink -> {
            var i = 0;
            while (i < body.length) {
                var len = Math.min(ThreadLocalRandom.current().nextInt(10), body.length - i);
                var buf = ByteBuffer.wrap(body, i, len);
                sink.next(buf);
                i += len;
            }
            sink.complete();
        });

        var request = new SimpleHttpServerRequest("POST", "/", f, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=X-INSOMNIA-BOUNDARY")
        }, Map.of());
        var result = MultipartReader.read(request)
            .collectList()
            .block();

        assertThat(result)
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field1");
                assertThat(part.fileName()).isNull();
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value1");
            }, Index.atIndex(0))
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field2");
                assertThat(part.fileName()).isEqualTo("example.txt");
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value2");
            }, Index.atIndex(1));
    }

    @Test
    void filenetMultipart() {
        var body = """
            --A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z
            Content-Disposition: form-data; name="field1"
            Content-Type: text/plain
            
            value1
            --A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z
            Content-Disposition: form-data; name="field2"; filename="example.txt"
            Content-Type: text/plain
            
            """;
        var length =  12;
        var f = Flux.fromStream(body.lines())
            .map(l -> StandardCharsets.UTF_8.encode(l + "\r\n"))
            .concatWith(Flux.fromStream(IntStream.range(0,length).mapToObj(i -> {
                var b = new byte[1024 * 1024];
                ThreadLocalRandom.current().nextBytes(b);
                return ByteBuffer.wrap(b);
            })))
            .concatWithValues(StandardCharsets.UTF_8.encode("\r\n--A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z--\r\n\r\n"));

        var request = new SimpleHttpServerRequest("POST", "/", f, new Map.Entry[]{
            Map.entry("content-type", " multipart/related; boundary=A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z; type=\"application/xop+xml\"; start-info=\"application/soap+xml\"")
        }, Map.of());
        var result = MultipartReader.read(request)
            .collectList()
            .block();

        assertThat(result)
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field1");
                assertThat(part.fileName()).isNull();
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo("value1");
            }, Index.atIndex(0))
            .satisfies(part -> {
                assertThat(part.name()).isEqualTo("field2");
                assertThat(part.fileName()).isEqualTo("example.txt");
                assertThat(part.contentType()).isEqualTo("text/plain");
                assertThat(part.content()).withRepresentation(new Representation() {
                    @Override
                    public String toStringOf(Object object) {
                        return "<array>";
                    }

                    @Override
                    public String unambiguousToStringOf(Object object) {
                        return "<array>";
                    }
                }).hasSize(length * 1024 * 1024);
            }, Index.atIndex(1));
    }

    static class SimpleHttpServerRequest implements HttpServerRequest {
        private final String method;
        private final String path;
        private final Flux<ByteBuffer> body;
        private final Map.Entry<String, String>[] headers;
        private final Map<String, String> routeParams;

        public SimpleHttpServerRequest(String method, String path, Flux<ByteBuffer> body, Map.Entry<String, String>[] headers, Map<String, String> routeParams) {
            this.method = method;
            this.path = path;
            this.body = body;
            this.headers = headers;
            this.routeParams = routeParams;
        }

        @Override
        public String method() {
            return method;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public HttpHeaders headers() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map.Entry<String, List<String>>[] entries = new Map.Entry[headers.length];
            for (int i = 0; i < headers.length; i++) {
                entries[i] = Map.entry(headers[i].getKey(), List.of(headers[i].getValue()));
            }
            return HttpHeaders.of(entries);
        }

        @Override
        public Map<String, Deque<String>> queryParams() {
            var questionMark = path.indexOf('?');
            if (questionMark < 0) {
                return Map.of();
            }
            var params = path.substring(questionMark + 1);
            return Stream.of(params.split("&"))
                .map(param -> {
                    var eq = param.indexOf('=');
                    if (eq <= 0) {
                        return Map.entry(param, new ArrayDeque<String>(0));
                    }
                    var name = param.substring(0, eq);
                    var value = param.substring(eq + 1);
                    return Map.entry(name, new ArrayDeque<>(List.of(value)));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (d1, d2) -> {
                    var d3 = new ArrayDeque<>(d1);
                    d3.addAll(d2);
                    return d3;
                }));
        }

        @Override
        public Map<String, String> pathParams() {
            return routeParams;
        }

        @Override
        public Flux<ByteBuffer> body() {
            return body;
        }

    }
}
