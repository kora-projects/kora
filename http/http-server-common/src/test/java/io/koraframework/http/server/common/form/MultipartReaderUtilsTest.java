package io.koraframework.http.server.common.form;

import io.koraframework.http.server.common.request.form.MultipartReaderUtils;
import org.assertj.core.data.Index;
import org.assertj.core.presentation.Representation;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.cookie.Cookies;
import io.koraframework.http.common.form.FormMultipart.FormPart.MultipartFile;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;

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

class MultipartReaderUtilsTest {
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
        var result = MultipartReaderUtils.read(request);

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
        var result = MultipartReaderUtils.read(request);

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
        var result = MultipartReaderUtils.read(request);

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
        var result = MultipartReaderUtils.read(request);

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

    @RepeatedTest(20)
    void contentDispositionParametersWithoutSpaceAfterSemicolon() throws IOException {
        var body = """
            --B\r
            Content-Disposition: form-data;name="f"\r
            \r
            v1\r
            --B\r
            Content-Disposition: form-data;name="g";filename="a.txt"\r
            \r
            v2\r
            --B\r
            Content-Disposition: form-data; filename="b.txt"; name="h"\r
            \r
            v3\r
            --B--\r
            """;
        var result = read("multipart/form-data; boundary=B", body);

        assertThat(result).hasSize(3);
        assertPart(result.get(0), "f", null, "v1");
        assertPart(result.get(1), "g", "a.txt", "v2");
        assertPart(result.get(2), "h", "b.txt", "v3");
    }

    @Test
    void boundaryParameterNameIsCaseInsensitive() throws IOException {
        var body = """
            --Bnd\r
            Content-Disposition: form-data; name="f"\r
            \r
            v1\r
            --Bnd--\r
            """;
        var result = read("multipart/form-data; Boundary=Bnd", body);

        assertThat(result).hasSize(1);
        assertPart(result.get(0), "f", null, "v1");
    }

    @RepeatedTest(20)
    void preambleAndTransportPaddingAreIgnored() throws IOException {
        var body = """
            This is the preamble.\r
            --Bnd-but-not-a-delimiter\r
            --Bnd \t \r
            Content-Disposition: form-data; name="f"\r
            \r
            v1\r
            --Bnd-in-body\r
            --Bnd  \r
            Content-Disposition: form-data; name="g"\r
            \r
            v2\r
            --Bnd--\r
            """;
        var result = read("multipart/form-data; boundary=Bnd", body);

        assertThat(result).hasSize(2);
        assertPart(result.get(0), "f", null, "v1\r\n--Bnd-in-body");
        assertPart(result.get(1), "g", null, "v2");
    }

    @Test
    @Timeout(10)
    void longTransportPaddingIsScannedOnce() throws IOException {
        var padding = " ".repeat(16 * 1024 * 1024);
        var body = "--Bnd" + padding + "\r\n"
            + "Content-Disposition: form-data; name=\"f\"\r\n\r\nv1\r\n"
            + "--Bnd" + padding + "\r\n"
            + "Content-Disposition: form-data; name=\"g\"\r\n\r\nv2\r\n"
            + "--Bnd--\r\n";
        // plain stream: the reader consumes the body in 16 KiB chunks
        var request = new SimpleHttpServerRequest("POST", "/", new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)), new Map.Entry[]{
            Map.entry("content-type", "multipart/form-data; boundary=Bnd")
        }, Map.of());
        var result = MultipartReaderUtils.read(request);

        assertThat(result).hasSize(2);
        assertPart(result.get(0), "f", null, "v1");
        assertPart(result.get(1), "g", null, "v2");
    }

    private static List<MultipartFile> read(String contentType, String body) throws IOException {
        var is = new RandomizedReadInputStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        var request = new SimpleHttpServerRequest("POST", "/", is, new Map.Entry[]{
            Map.entry("content-type", contentType)
        }, Map.of());
        return MultipartReaderUtils.read(request);
    }

    private static void assertPart(MultipartFile part, String name, @Nullable String fileName, String content) {
        assertThat(part.name()).isEqualTo(name);
        assertThat(part.fileName()).isEqualTo(fileName);
        assertThat(part.content()).asString(StandardCharsets.UTF_8).isEqualTo(content);
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
        public String host() {
            return "localhost";
        }

        @Override
        public String scheme() {
            return "http";
        }

        @Override
        public long requestStartTimeInNanos() {
            return 0;
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
        public String pathTemplate() {
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
        public Map<String, List<String>> queryParams() {
            var questionMark = path.indexOf('?');
            if (questionMark < 0) {
                return Map.of();
            }
            var params = path.substring(questionMark + 1);
            return Stream.of(params.split("&"))
                .map(param -> {
                    var eq = param.indexOf('=');
                    if (eq <= 0) {
                        return Map.entry(param, new ArrayList());
                    }
                    var name = param.substring(0, eq);
                    var value = param.substring(eq + 1);
                    return Map.entry(name, new ArrayList<>(List.of(value)));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (d1, d2) -> {
                    var d3 = new ArrayList<>(d1);
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
            var realLen = len - ThreadLocalRandom.current().nextInt(len - 1);
            return super.read(b, off, realLen);
        }
    }
}
