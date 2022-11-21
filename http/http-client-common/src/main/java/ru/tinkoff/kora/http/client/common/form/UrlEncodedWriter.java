package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UrlEncodedWriter {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public void add(String key, String value) {
        if (this.baos.size() > 0) {
            this.baos.write('&');
        }
        this.baos.writeBytes(URLEncoder.encode(key, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        this.baos.write('=');
        this.baos.writeBytes(URLEncoder.encode(value, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    }

    public HttpClientRequestBuilder write(HttpClientRequestBuilder b) {
        var data = this.baos.toByteArray();
        return b
            .header("content-type", "application/x-www-form-urlencoded")
            .header("content-length", Integer.toString(data.length))
            .body(data);
    }
}
