package io.koraframework.http.client.common.request.form;

import io.koraframework.http.common.body.DefaultFullHttpBody;
import io.koraframework.http.common.body.HttpBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FormUrlEncodedWriter implements AutoCloseable {

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public void add(String key, String value) {
        if (this.baos.size() > 0) {
            this.baos.write('&');
        }
        this.baos.writeBytes(URLEncoder.encode(key, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        this.baos.write('=');
        this.baos.writeBytes(URLEncoder.encode(value, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <b>Русский</b>: Добавляет одно поле, значения которого соединены разделителем: каждое значение кодируется отдельно, а разделитель пишется как есть
     * <hr>
     * <b>English</b>: Adds one field whose values are joined by the delimiter: each value is encoded on its own and the delimiter is written as is
     * <br>
     * <br>
     * Пример / Example: <code>add("ids", ",", List.of("1", "2"))</code> -> <code>ids=1,2</code>
     */
    public void add(String key, String delimiter, Iterable<String> values) {
        if (this.baos.size() > 0) {
            this.baos.write('&');
        }
        this.baos.writeBytes(URLEncoder.encode(key, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        this.baos.write('=');
        // a space can't be written as is, and `+` is a space of a value
        var rawDelimiter = (" ".equals(delimiter) ? "%20" : delimiter).getBytes(StandardCharsets.UTF_8);
        var first = true;
        for (var value : values) {
            if (!first) {
                this.baos.writeBytes(rawDelimiter);
            }
            first = false;
            this.baos.writeBytes(URLEncoder.encode(value, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        }
    }

    public DefaultFullHttpBody write() {
        var data = this.baos.toByteArray();
        return HttpBody.of("application/x-www-form-urlencoded", data);
    }

    @Override
    public void close() throws IOException {
        baos.close();
    }
}
