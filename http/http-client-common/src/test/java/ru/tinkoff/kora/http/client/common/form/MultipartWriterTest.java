package ru.tinkoff.kora.http.client.common.form;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.form.FormMultipart;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartWriterTest {
    @Test
    void testMultipart() {
        var e = """
            --boundary\r
            content-disposition: form-data; name="field1"\r
            content-type: text/plain; charset=utf-8\r
            \r
            value1\r
            --boundary\r
            content-disposition: form-data; name="field2"; filename="example1.txt"\r
            content-type: text/plain\r
            \r
            value2\r
            --boundary\r
            content-disposition: form-data; name="field3"; filename="example2.txt"\r
            content-type: text/plain\r
            \r
            value3\r
            --boundary--""";
        var b = MultipartWriter.write(HttpClientRequest.post("/test"), "boundary", List.of(
            FormMultipart.data("field1", "value1"),
            FormMultipart.file("field2", "example1.txt", "text/plain", "value2".getBytes(StandardCharsets.UTF_8)),
            FormMultipart.file("field3", "example2.txt", "text/plain", "value3".getBytes(StandardCharsets.UTF_8))
        )).build();
        var s = ReactorUtils.toByteArrayMono(b.body())
            .map(_b -> new String(_b, StandardCharsets.UTF_8))
            .block();
        assertThat(s).isEqualTo(e);
        assertThat(b.headers().getFirst("content-type")).isEqualTo("multipart/form-data;boundary=\"boundary\"");
    }
}
