package ru.tinkoff.kora.http.server.common.form;

import jakarta.annotation.Nullable;
import org.assertj.core.data.Index;
import org.assertj.core.presentation.Representation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.cookie.Cookies;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartReaderTest {
    @RepeatedTest(100)
    void test() throws Exception {
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
        var bais = new ByteArrayInputStream(e);
        var is = new RandomizedReadInputStream(bais);

        var request = new SimpleHttpServerRequest("POST", "/", is, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=\"boundary\"")
        }, Map.of());
        var result = MultipartReader.read(request);

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
    void lastBytesErrorTest() throws IOException {
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
        var bais = new ByteArrayInputStream(e);
        var is = new RandomizedReadInputStream(bais);

        var request = new SimpleHttpServerRequest("POST", "/", is, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=\"boundary\"")
        }, Map.of());
        var result = MultipartReader.read(request);

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
    void insomniaMultipart() throws IOException {
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
        var bais = new ByteArrayInputStream(body);
        var is = new RandomizedReadInputStream(bais);

        var request = new SimpleHttpServerRequest("POST", "/", is, new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=X-INSOMNIA-BOUNDARY")
        }, Map.of());
        var result = MultipartReader.read(request);

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
    void filenetMultipart() throws IOException {
        var body = """
            --A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z
            Content-Disposition: form-data; name="field1"
            Content-Type: text/plain
            
            value1
            --A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z
            Content-Disposition: form-data; name="field2"; filename="example.txt"
            Content-Type: text/plain
            
            """;
        var baos = new ByteArrayOutputStream();
        baos.write(body.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 12; i++) {
            var b = new byte[1024 * 1024];
            ThreadLocalRandom.current().nextBytes(b);
            baos.write(b);
        }
        baos.write("\r\n--A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z--\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        var bais = new ByteArrayInputStream(baos.toByteArray());
        var is = new RandomizedReadInputStream(bais);

        var request = new SimpleHttpServerRequest("POST", "/", is, new Map.Entry[]{
            Map.entry("content-type", " multipart/related; boundary=A-B--MIME-BOUNDARY--b96857b1a70d2dc4-17e9b561ad7--Y-Z; type=\"application/xop+xml\"; start-info=\"application/soap+xml\"")
        }, Map.of());
        var result = MultipartReader.read(request);

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
                }).hasSize(12 * 1024 * 1024);
            }, Index.atIndex(1));
    }

    static class SimpleHttpServerRequest implements HttpServerRequest {
        private final String method;
        private final String path;
        private final InputStream body;
        private final Map.Entry<String, String>[] headers;
        private final Map<String, String> routeParams;

        public SimpleHttpServerRequest(String method, String path, InputStream body, Map.Entry<String, String>[] headers, Map<String, String> routeParams) {
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
        public String route() {
            return this.path;
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
        public List<Cookie> cookies() {
            var list = new ArrayList<Cookie>();
            var cookies = headers().getAll("Cookies");
            Cookies.parseRequestCookies(500, true, cookies, list);
            return list;
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
        public HttpBodyInput body() {
            return new HttpBodyInput() {
                @Override
                public InputStream asInputStream() {
                    return body;
                }

                @Override
                public long contentLength() {
                    return -1;
                }

                @Nullable
                @Override
                public String contentType() {
                    return null;
                }

                @Override
                public void close() throws IOException {

                }
            };
        }

    }

    private static class RandomizedReadInputStream extends InputStream {

        private final ByteArrayInputStream bais;

        public RandomizedReadInputStream(ByteArrayInputStream bais) {
            this.bais = bais;
        }

        @Override
        public int read() throws IOException {
            return bais.read();
        }

        @Override
        public int read(@NotNull byte[] b, int off, int len) throws IOException {
            var realLen = len - ThreadLocalRandom.current().nextInt(len - 1) + 1;
            return super.read(b, off, realLen);
        }
    }
}
