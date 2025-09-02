package ru.tinkoff.kora.http.client.common.form;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.form.FormMultipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartWriterTest {
    @Test
    void testMultipart() throws IOException {
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
            FormMultipart.file("field4", "example3.txt", new HttpBodyOutput() {
                @Override
                public void close() {}

                @Override
                public long contentLength() {
                    return -1;
                }

                @Override
                public String contentType() {
                    return "text/plain";
                }

                @Override
                public void write(OutputStream os) throws IOException {
                    os.write("some ".getBytes(StandardCharsets.UTF_8));
                    os.write("streaming ".getBytes(StandardCharsets.UTF_8));
                    os.write("data".getBytes(StandardCharsets.UTF_8));
                }
            }),
            FormMultipart.data("field5", "value5")
        ));
        var baos = new ByteArrayOutputStream();
        b.write(baos);
        var s = baos.toString(StandardCharsets.UTF_8);
        assertThat(s).isEqualTo(e);
        assertThat(b.contentType()).isEqualTo("multipart/form-data;boundary=\"boundary\"");
    }
}
