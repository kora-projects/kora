package ru.tinkoff.kora.http.client.common.form;

import org.junit.jupiter.api.Test;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import ru.tinkoff.kora.common.util.FlowUtils;
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
            --boundary\r
            content-disposition: form-data; name="field4"; filename="example3.txt"\r
            content-type: text/plain\r
            \r
            some streaming data\r
            --boundary\r
            content-disposition: form-data; name="field5"\r
            content-type: text/plain; charset=utf-8\r
            \r
            value5\r
            --boundary--""";
        var b = MultipartWriter.write("boundary", List.of(
            FormMultipart.data("field1", "value1"),
            FormMultipart.file("field2", "example1.txt", "text/plain", "value2".getBytes(StandardCharsets.UTF_8)),
            FormMultipart.file("field3", "example2.txt", "text/plain", "value3".getBytes(StandardCharsets.UTF_8)),
            FormMultipart.file("field4", "example3.txt", "text/plain", JdkFlowAdapter.publisherToFlowPublisher(Flux.just("some ", "streaming ", "data").map(StandardCharsets.UTF_8::encode))),
            FormMultipart.data("field5", "value5")
        ));
        var s = FlowUtils.toByteArrayFuture(b)
            .thenApply(_b -> new String(_b, StandardCharsets.UTF_8))
            .join();
        assertThat(s).isEqualTo(e);
        assertThat(b.contentType()).isEqualTo("multipart/form-data;boundary=\"boundary\"");
    }
}
